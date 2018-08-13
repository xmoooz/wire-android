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
package com.waz.zclient.messages.parts

import com.waz.utils.wrappers.AndroidURIUtil
import android.content.Context
import android.util.AttributeSet
import android.widget.{ImageView, LinearLayout, TextView}
import com.waz.model.{UserData, UserId}
import com.waz.service.IntegrationsService
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.zclient.common.controllers.BrowserController
import com.waz.zclient.common.views.ImageAssetDrawable.{RequestBuilder, ScaleType}
import com.waz.zclient.common.views.ImageController.{ImageSource, NoImage, WireImage}
import com.waz.zclient.common.views._
import com.waz.zclient.messages.{MessageViewPart, MsgPart, UsersController}
import com.waz.zclient.paintcode.ServicePlaceholderDrawable
import com.waz.zclient.ui.utils.TextViewUtils
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.{R, ViewHelper}

import scala.concurrent.Future

class ConnectRequestPartView(context: Context, attrs: AttributeSet, style: Int) extends LinearLayout(context, attrs, style) with MessageViewPart with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  import Threading.Implicits.Ui

  override val tpe: MsgPart = MsgPart.ConnectRequest

  lazy val chathead     : ImageView    = findById(R.id.cv__row_conversation__connect_request__chat_head)
  lazy val label        : TextView        = findById(R.id.ttv__row_conversation__connect_request__label)
  lazy val userDetails  : UserDetailsView = findById(R.id.udv__row_conversation__connect_request__user_details)

  private val browser = inject[BrowserController]
  private val users   = inject[UsersController]
  private val integrations = inject[Signal[IntegrationsService]]

  val members = message.map(m => m.members + m.userId)

  val user = for {
    self <- inject[Signal[UserId]]
    members <-  message.map(m => Set(m.userId) ++ Set(m.recipient).flatten)
    Some(user) <- members.find(_ != self).fold {
      Signal.const(Option.empty[UserData])
    } { uId =>
      users.user(uId).map(Some(_))
    }
  } yield user


  val integration = for {
    usr <- user
    intService <- integrations
    integration <- Signal.future((usr.integrationId, usr.providerId) match {
    case (Some(i), Some(p)) => intService.getIntegration(p, i).map(Some(_))
    case _ => Future.successful(None)
  })
  } yield integration


  val integrationImageSource: Signal[ImageSource] = integration.map(_.flatMap(_.asset.map(WireImage)).getOrElse(NoImage()))

  val integrationDrawable = new IntegrationAssetDrawable(
    src          = integrationImageSource,
    scaleType    = ScaleType.CenterInside,
    request      = RequestBuilder.Regular,
    background   = Some(ServicePlaceholderDrawable(getDimenPx(R.dimen.wire__padding__regular))),
    animate      = true)


  val userImageSource: Signal[ImageSource] = user.map(_.picture.map(WireImage).getOrElse(NoImage()))

  val userDrawable = new ImageAssetDrawable(userImageSource, scaleType = ScaleType.CenterInside, request = RequestBuilder.Round)

  integration.map{
    case Some(_) => integrationDrawable
    case _ => userDrawable
  }.onUi(chathead.setImageDrawable(_))

  user.map(_.id)(userDetails.setUserId)

  user.map(u => (u.isAutoConnect, u.isWireBot)).on(Threading.Ui) {
    case (true, _) =>
      label.setText(R.string.content__message__connect_request__auto_connect__footer)
      TextViewUtils.linkifyText(label, getStyledColor(R.attr.wirePrimaryTextColor), true, true, new Runnable() {
        override def run() = browser.openUrl(AndroidURIUtil parse getString(R.string.url__help))
      })
    case (false, false) =>
      label.setTextColor(getStyledColor(R.attr.wirePrimaryTextColor))
      label.setText(R.string.content__message__connect_request__footer)
    case (_, true) =>
      label.setTextColor(getColor(R.color.accent_red))
      label.setText(R.string.generic_service_warning)
  }

}
