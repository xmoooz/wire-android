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
package com.waz.services.calling

import android.app.Service
import android.content
import android.os.IBinder
import com.waz.zclient.ServiceHelper
import com.waz.zclient.notifications.controllers.CallingNotificationsController
import com.waz.zclient.notifications.controllers.CallingNotificationsController.androidNotificationBuilder
import com.waz.utils.events.Subscription

class CallingNotificationsService extends ServiceHelper {
  private lazy val callNCtrl = inject[CallingNotificationsController]
  private var sub = Option.empty[Subscription]

  override def onBind(intent: content.Intent): IBinder = null

  override def onStartCommand(intent: content.Intent, flags: Int, startId: Int): Int = {
    implicit val cxt: content.Context = getApplicationContext

    super.onStartCommand(intent, flags, startId)
    if (sub.isEmpty) {
      sub = Some(callNCtrl.notifications.map(_.find(_.isMainCall)).onUi {
        case Some(not) =>
          val builder = androidNotificationBuilder(not)
          startForeground(not.convId.str.hashCode, builder.build())
        case _ =>
          stopForeground(true)
          stopSelf()
      })
    }
    Service.START_STICKY
  }
}
