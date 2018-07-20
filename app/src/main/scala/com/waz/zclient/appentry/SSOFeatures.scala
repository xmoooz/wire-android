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
package com.waz.zclient.appentry

import android.support.v4.app.Fragment
import com.waz.ZLog.verbose
import com.waz.ZLog.ImplicitTag._
import com.waz.utils.events.Subscription
import com.waz.service.SSOService
import com.waz.utils.events.EventContext
import com.waz.zclient.InputDialog.{Event, OnNegativeBtn, OnPositiveBtn, ValidatorResult}
import com.waz.zclient._

import scala.concurrent.Future

object SSOFeatures {
  val SSODialogTag = "SSO_DIALOG"
}

trait SSOFeatures extends CanInject with LifecycleStartStop with HasChildFragmentManager {

  import SSOFeatures._
  import com.waz.threading.Threading.Implicits.Background
  import com.waz.threading.Threading

  private lazy val clipboard   = inject[ClipboardUtils]
  private lazy val ssoService  = inject[SSOService]

  private lazy val dialogStaff = new InputDialog.Listener with InputDialog.InputValidator {
    override def onDialogEvent(event: Event): Unit = event match {
      case OnNegativeBtn => verbose("Negative")
      case OnPositiveBtn(input) =>
        ssoService.extractUUID(input).foreach { uuid =>
          showFragment(SSOWebViewFragment.newInstance(uuid.toString), SSOWebViewFragment.Tag)
        }

    }
    override def isInputInvalid(input: String): ValidatorResult =
      if (ssoService.isTokenValid(input.trim)) ValidatorResult.Valid
      else ValidatorResult.Invalid()
  }

  private var clipboardSubscription: Subscription = _

  override protected def onStartCalled(): Unit = {
    super.onStartCalled()
    findChildFragment[InputDialog](SSODialogTag).foreach(_.setListener(dialogStaff).setValidator(dialogStaff))
    clipboardSubscription = clipboard.primaryClipChanged.onUi { _ => showSSODialogIfNeeded() } (EventContext.Global)
    showSSODialogIfNeeded()
  }

  override protected def onStopCalled(): Unit = {
    super.onStopCalled()
    clipboardSubscription.destroy()
  }

  private def extractTokenFromClipboard: Future[Option[String]] = {
    Future {
      for {
        clipboardText <- clipboard.getPrimaryClipItemsAsText.headOption
        token <- ssoService.extractToken(clipboardText.toString)
      } yield token
    }
  }

  protected def showSSODialogIfNeeded(): Unit = {
    if (findChildFragment[InputDialog](SSODialogTag).isEmpty) {
      extractTokenFromClipboard.filter(_.nonEmpty).foreach(showSSODialog)(Threading.Ui)
    }
  }

  protected def showSSODialog(token: Option[String]): Unit = {
    InputDialog
      .newInstance(
        title = R.string.app_entry_sso_dialog_title,
        message = R.string.app_entry_sso_dialog_message,
        inputValue = token,
        inputHint = Some(R.string.app_entry_sso_input_hint),
        validateInput = true,
        disablePositiveBtnOnInvalidInput = true,
        negativeBtn = R.string.app_entry_dialog_cancel,
        positiveBtn = R.string.app_entry_dialog_log_in
      )
      .setListener(dialogStaff)
      .setValidator(dialogStaff)
      .show(childFragmentManager, SSODialogTag)
  }

  protected def cancelSSODialog(): Unit = {
    findChildFragment[InputDialog](SSODialogTag).foreach(_.dismissAllowingStateLoss())
  }

  protected def showFragment(f: => Fragment, tag: String, animated: Boolean = true): Unit

}
