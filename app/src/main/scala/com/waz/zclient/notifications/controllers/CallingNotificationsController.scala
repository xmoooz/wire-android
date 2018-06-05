/**
  * Wire
  * Copyright (C) 2018 Wire Swiss GmbH
  *
  * This program is free software: you can redistribute it and/or modify
  * it under the terms of the GNU General Public License as published by
  * the Free Software Foundation, either version 3 of the License, or
  * (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful,
  * but WITHOUT ANY WARRANTY; without even the implied warranty of
  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  * GNU General Public License for more details.
  *
  * You should have received a copy of the GNU General Public License
  * along with this program.  If not, see <http://www.gnu.org/licenses/>.
  */
package com.waz.zclient.notifications.controllers

import android.app.{NotificationManager, PendingIntent}
import android.graphics.{Bitmap, Color}
import android.support.v4.app.NotificationCompat
import com.waz.ZLog._
import com.waz.bitmap.BitmapUtils
import com.waz.model.{ConvId, UserId}
import com.waz.service.assets.AssetService.BitmapResult.BitmapLoaded
import com.waz.service.call.CallInfo
import com.waz.service.call.CallInfo.CallState._
import com.waz.service.{AccountsService, ZMessaging}
import com.waz.ui.MemoryImageCache.BitmapRequest.Regular
import com.waz.utils.LoggedTry
import com.waz.utils.events.{EventContext, Signal}
import com.waz.utils.wrappers.{Context, Intent}
import com.waz.zclient.Intents.{CallIntent, OpenCallingScreen}
import com.waz.zclient._
import com.waz.zclient.calling.controllers.CallController
import com.waz.zclient.common.views.ImageController
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.{DeprecationUtils, RingtoneUtils}
import com.waz.zms.CallWakeService
import org.threeten.bp.Instant
import com.waz.utils._

import scala.util.control.NonFatal

class CallingNotificationsController(implicit cxt: WireContext, eventContext: EventContext, inj: Injector) extends Injectable {

  import CallingNotificationsController._

  val callImageSizePx = toPx(CallImageSizeDp)

  val notificationManager = inject[NotificationManager]

  val callCtrler = inject[CallController]
  import callCtrler._

  val notifications =
    (for {
      curCallId <- currentCall.map(_.convId)
      zs        <- inject[AccountsService].zmsInstances
      allCalls <- Signal.sequence(zs.map(_.calling.availableCalls).toSeq:_*).map(_.flatten.toMap).map {
        _.values
          .filter(c => c.state.contains(OtherCalling) || c.convId == curCallId)
          .map(c => c.convId -> (c.caller, c.account))
      }
      bitmaps <- Signal.sequence(allCalls.map { case (conv, (caller, account)) =>
          zs.find(_.selfUserId == account).fold2(Signal.const(conv -> Option.empty[Bitmap]), z => getBitmapSignal(z, caller).map(conv -> _))
      }.toSeq: _*).map(_.toMap)
      notInfo <- Signal.sequence(allCalls.map { case (conv, (caller, account)) =>
        zs.find(_.selfUserId == account).fold2(Signal.const(Option.empty[CallInfo], "", ""),
          z => Signal(z.calling.availableCalls.map(_.get(conv)),
            z.usersStorage.optSignal(caller).map(_.map(_.name).getOrElse("")),
            z.convsStorage.optSignal(conv).map(_.map(_.displayName).getOrElse("")))).map(conv -> _)
      }.toSeq: _*)
      notificationData = notInfo.collect {
        case (convId, (Some(callInfo), title, msg)) =>
          val action = callInfo.state match {
            case Some(OtherCalling) => NotificationAction.DeclineOrJoin
            case Some(SelfConnected | SelfCalling | SelfJoining) => NotificationAction.Leave
            case _ => NotificationAction.Nothing
          }
          CallNotification(
            convId.str.hashCode,
            convId,
            callInfo.account,
            callInfo.startTime,
            title,
            msg,
            bitmaps.getOrElse(convId, None),
            convId == curCallId,
            action)
      }
    } yield notificationData.sortWith {
      case (cn1, _) if cn1.convId == curCallId => false
      case (_, cn2) if cn2.convId == curCallId => true
      case (cn1, cn2) => cn1.convId.str > cn2.convId.str
    }).orElse(Signal.const(Seq.empty[CallNotification]))

  private var currentNotifications = Set.empty[Int]

  notifications.map(_.exists(!_.isMainCall)).onUi(soundController.playRingFromThemInCall)

  notifications.onUi { nots =>
    verbose(s"${nots.size} call notifications")
    val toCancel = currentNotifications -- nots.map(_.id).toSet
    toCancel.foreach(notificationManager.cancel(CallNotificationTag, _))

    nots.foreach { not =>
      verbose(s"call not: $not")
      val builder = DeprecationUtils.getBuilder(cxt)
        .setSmallIcon(R.drawable.call_notification_icon)
        .setLargeIcon(not.bitmap.orNull)
        .setContentTitle(not.title)
        .setContentText(not.msg)
        .setContentIntent(OpenCallingScreen())
        .setStyle(new NotificationCompat.BigTextStyle()
          .setBigContentTitle(not.title)
          .bigText(not.msg))
        .setCategory(NotificationCompat.CATEGORY_CALL)
        .setPriority(if (not.isMainCall) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_MAX) //incoming calls go higher up in the list)
        .setOnlyAlertOnce(true)
        .setOngoing(true)

      if (!not.isMainCall) {
        builder.setDefaults(NotificationCompat.DEFAULT_LIGHTS | NotificationCompat.DEFAULT_VIBRATE)
        builder.setSound(RingtoneUtils.getUriForRawId(cxt, R.raw.empty_sound))
      }

      not.action match {
        case NotificationAction.DeclineOrJoin =>
          builder
            .addAction(R.drawable.ic_menu_silence_call_w, getString(R.string.system_notification__silence_call), createEndIntent(not.accountId, not.convId))
            .addAction(R.drawable.ic_menu_join_call_w, getString(R.string.system_notification__join_call), if (not.isMainCall) createJoinIntent(not.accountId, not.convId) else CallIntent(not.accountId, not.convId))

        case NotificationAction.Leave =>
          builder.addAction(R.drawable.ic_menu_end_call_w, getString(R.string.system_notification__leave_call), createEndIntent(not.accountId, not.convId))

        case _ => //no available action
      }

      def showNotification() = {
        currentNotifications += not.id
        notificationManager.notify(CallNotificationTag, not.id, builder.build())
      }

      LoggedTry(showNotification()).recover {
        case NonFatal(e) =>
          error(s"Notify failed: try without bitmap", e)
          builder.setLargeIcon(null)
          try showNotification()
          catch {
            case NonFatal(e2) => error("second display attempt failed, aborting", e2)
          }
      }
    }
  }

  private def getBitmapSignal(z: ZMessaging, caller: UserId) = for {
      Some(id) <- z.usersStorage.optSignal(caller).map(_.flatMap(_.picture))
      bitmap   <- inject[ImageController].imageSignal(z, id, Regular(callImageSizePx))
    } yield
      bitmap match {
        case BitmapLoaded(bmp, _) => Option(BitmapUtils.createRoundBitmap(bmp, callImageSizePx, 0, Color.TRANSPARENT))
        case _ => None
      }

  private def getCallStateMessage(call: CallInfo): String =
    getString((call.stateCollapseJoin, call.isVideoCall) match {
      case (Some(SelfCalling),   true)  => R.string.system_notification__outgoing_video
      case (Some(SelfCalling),   false) => R.string.system_notification__outgoing
      case (Some(OtherCalling),  true)  => R.string.system_notification__incoming_video
      case (Some(OtherCalling),  false) => R.string.system_notification__incoming
      case (Some(SelfConnected), _)     => R.string.system_notification__ongoing
      case _                            => R.string.empty_string
    })

  private def createJoinIntent(account: UserId, convId: ConvId) = pendingIntent(JoinCallRequestCode, CallWakeService.joinIntent(Context.wrap(cxt), account, convId))
  private def createEndIntent(account: UserId, convId: ConvId) = pendingIntent(EndCallRequestCode, CallWakeService.silenceIntent(Context.wrap(cxt), account, convId))

  private def pendingIntent(reqCode: Int, intent: Intent) = PendingIntent.getService(cxt, reqCode, Intent.unwrap(intent), PendingIntent.FLAG_UPDATE_CURRENT)
}

object CallingNotificationsController {

  case class CallNotification(id:            Int,
                              convId:        ConvId,
                              accountId:     UserId,
                              callStartTime: Instant,
                              title:         String,
                              msg:           String,
                              bitmap:        Option[Bitmap],
                              isMainCall:    Boolean,
                              action:        NotificationAction)


  object NotificationAction extends Enumeration {
    val DeclineOrJoin, Leave, Nothing = Value
  }
  type NotificationAction = NotificationAction.Value

  val CallNotificationTag = "call_notification"

  val CallImageSizeDp = 64

  val JoinCallRequestCode = 8912
  val EndCallRequestCode = 8914
  private implicit val tag: LogTag = logTagFor[CallingNotificationsController]
}
