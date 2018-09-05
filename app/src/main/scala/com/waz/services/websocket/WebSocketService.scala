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
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog._
import com.waz.content.GlobalPreferences.PushEnabledKey
import com.waz.service.AccountsService.InForeground
import com.waz.service.{AccountsService, GlobalModule}
import com.waz.utils.events.Signal
import com.waz.zclient.notifications.controllers.NotificationManagerWrapper
import com.waz.zclient.{R, ServiceHelper}

/**
  * Receiver called on boot or when app is updated.
  */
class WebSocketBroadcastReceiver extends BroadcastReceiver {
  override def onReceive(context: Context, intent: Intent): Unit = {
    verbose(s"onReceive $intent")
    WebSocketService(context)
  }
}


/**
  * Service keeping the process running as long as web socket should be connected.
  */
class WebSocketService extends ServiceHelper {

  private implicit def context = getApplicationContext

  private lazy val launchIntent = PendingIntent.getActivity(context, 1, getPackageManager.getLaunchIntentForPackage(context.getPackageName), 0)

  private lazy val global   = inject[GlobalModule]
  private lazy val accounts = inject[AccountsService]

  private lazy val webSocketActiveSubscription =
    (for {
      gpsAvailable     <- global.googleApi.isGooglePlayServicesAvailable
      cloudPushEnabled <- global.prefs(PushEnabledKey).signal
      accs             <- accounts.zmsInstances
      accsInFG         <- Signal.sequence(accs.map(_.selfUserId).map(id => accounts.accountState(id).map(st => id -> st)).toSeq : _*)
    } yield (!gpsAvailable || !cloudPushEnabled, accs, accsInFG.toMap)) {
      case (cloudPushDisabled, accs, accsInFg) =>
        val (zmsWithWSActive, zmsWithWSInactive) = accs.partition(zms => accsInFg(zms.selfUserId) == InForeground || cloudPushDisabled)
        verbose(s"cloudPushDisabled: $cloudPushDisabled, zmsWithWSActive: ${zmsWithWSActive.map(_.selfUserId)}, zmsWithWSInactive: ${zmsWithWSInactive.map(_.selfUserId)}")

        zmsWithWSActive.foreach(_.wsPushService.activate())
        zmsWithWSInactive.foreach(_.wsPushService.deactivate())

        if (zmsWithWSActive.isEmpty) {
          verbose("stopping")
          stopSelf()
        }
    }

  private lazy val appInForegroundSubscription =
    global.lifecycle.uiActive.zip(global.network.isOnline) {
      case (true, _) =>
        verbose("stopForeground")
        stopForeground(true)
      case (false, online) =>
        verbose("startForeground")
        startForeground(WebSocketService.ForegroundId,
          new NotificationCompat.Builder(this, NotificationManagerWrapper.OngoingNotificationsChannelId)
            .setSmallIcon(R.drawable.ic_menu_logo)
            .setContentTitle(getString(if (online) R.string.zms_websocket_connected else R.string.zms_websocket_connection_offline))
            .setContentText(getString(R.string.zms_websocket_connection_info))
            .setContentIntent(launchIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        )
    }


  override def onBind(intent: content.Intent): IBinder = null

  override def onStartCommand(intent: Intent, flags: Int, startId: Int): Int = {
    verbose(s"onStartCommand($intent, $startId)")
    webSocketActiveSubscription
    appInForegroundSubscription

    Service.START_STICKY
  }
}

object WebSocketService {
  val ForegroundId = 41235

  def apply(context: Context) = context.startService(new Intent(context, classOf[WebSocketService]))
}
