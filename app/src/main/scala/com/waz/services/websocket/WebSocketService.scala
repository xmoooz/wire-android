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

package com.waz.services.websocket

import android.app.{PendingIntent, Service}
import android.content
import android.content.{BroadcastReceiver, Context, Intent}
import android.os.IBinder
import android.support.v4.app.NotificationCompat
import android.support.v4.app.NotificationCompat._
import com.github.ghik.silencer.silent
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog._
import com.waz.content.GlobalPreferences.PushEnabledKey
import com.waz.service.AccountsService.InForeground
import com.waz.service.GlobalModule
import com.waz.service.push.WSPushService
import com.waz.utils.events.Signal
import com.waz.zclient.ServiceHelper
import com.waz.zclient.notifications.controllers.NotificationManagerWrapper.ChannelId

/**
  * Receiver called on boot or when app is updated.
  */
class WebSocketBroadcastReceiver extends BroadcastReceiver {
  override def onReceive(context: Context, intent: Intent): Unit = {
    verbose(s"onReceive $intent")
    context.startService(new Intent(context, classOf[WebSocketService]))
    //WakefulBroadcastReceiver.startWakefulService(context, new Intent(context, classOf[WebSocketService]))
  }
}


/**
  * Service keeping the process running as long as web socket should be connected.
  */
class WebSocketService extends ServiceHelper {
  import com.waz.threading.Threading.Implicits.Background

  private implicit def context = getApplicationContext

  private lazy val launchIntent = PendingIntent.getActivity(context, 1, getPackageManager.getLaunchIntentForPackage(context.getPackageName), 0)

/*
  (for {
    Some(zms) <- zmessaging
    false <- zms.pushToken.pushActive
    true <- zms.prefs.preference(WsForegroundKey).signal // only when foreground service is enabled
    offline <- zms.network.networkMode.map(_ == NetworkMode.OFFLINE)
    connected <- zms.wsPushService.connected
  } yield Option(
    if (offline) R.string.zms_websocket_connection_offline
    else if (connected) R.string.zms_websocket_connected
    else R.string.zms_websocket_connecting
  )).orElse(Signal const None).onUi {
    case None =>
      verbose("stopForeground")
      stopForeground(true)
    case Some(state) =>
      verbose(s"startForeground $state")
      startForeground(ForegroundId, getNotificationCompatBuilder(context)
        .setSmallIcon(R.drawable.ic_menu_logo)
        .setContentTitle(context.getResources.getString(state))
        .setContentText(context.getResources.getString(R.string.zms_websocket_connection_info))
        .setContentIntent(launchIntent)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .build()
      )
  }
*/

  private lazy val global = inject[GlobalModule]

  private lazy val accountsActive = global.accountsService.accountsWithManagers.map(_.nonEmpty)

  private lazy val webSocketActive =
    (for {
      true <- global.googleApi.isGooglePlayServicesAvailable
      true <- global.prefs(PushEnabledKey).signal
      true <- accountsActive
    } yield true).orElse(Signal.const(false)) {
      case true => wsPushService(_.activate())
      case => wsPushService(_.deactivate())
    }

  private lazy val appInForeground = for {
    zms          <- global.accountsService.activeZms
    inForeground <- zms.fold(Signal.const(false))(z => global.accountsService.accountState(z.selfUserId).map(_ == InForeground))
  } yield inForeground


  override def onBind(intent: content.Intent): IBinder = null

  override def onStartCommand(intent: Intent, flags: Int, startId: Int): Int = {
    verbose(s"onStartCommand($intent, $startId)")

    accountsActive.head.foreach {
      case false =>
        verbose(s"no active account")
        wsPushService(_.deactivate())
        stopForeground(true)
        stopSelf()
      case true =>
        Signal(webSocketActive, appInForeground).head.foreach {
          case (true, _) =>
            verbose(s"gps available")
            wsPushService(_.deactivate())
            stopForeground(true)
            stopSelf()
          case (false, true) =>
            verbose(s"gps not available, the app is in foreground")
            wsPushService(_.activate())
          case (false, false) =>
            verbose(s"gps not available, the app is in background")
            //wsPushService(_.activate())
            //val np = NotificationProps()
            //startForeground(WebSocketService.ForegroundId, )
        }
    }

    Service.START_STICKY
  }

  private def wsPushService(f: WSPushService => Unit) =
    global.accountsService.activeZms.map(_.map(_.wsPushService)).head.foreach {
      case Some(ws) => f(ws)
      case _ =>
    }

/*  private lazy val stickyNotification = {
    val title = "title"
    val message = "message"

    val builder = new NotificationCompat.Builder(context, NotificationManagerWrapper.StickyNotificationsChannelId)
      .setSmallIcon(R.drawable.call_notification_icon)
      .setLargeIcon(not.bitmap.orNull)
      .setContentTitle(title)
      .setContentText(message)
      .setContentIntent(OpenCallingScreen())
      .setStyle(new NotificationCompat.BigTextStyle()
        .setBigContentTitle(title)
        .bigText(message))
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
  }*/

  @silent def getNotificationCompatBuilder(context: Context): Builder = new NotificationCompat.Builder(context, ChannelId)
}

object WebSocketService {
  val ForegroundId = 41235

  def apply(context: Context) = context.startService(new Intent(context, classOf[WebSocketService]))
}
