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
package com.waz.zclient.participants.fragments

import android.os.Bundle
import android.view.{LayoutInflater, View, ViewGroup}
import com.waz.zclient.utils.RichView
import android.widget.LinearLayout
import com.waz.service.ZMessaging
import com.waz.utils.events.Signal
import com.waz.zclient.ui.text.{GlyphTextView, TypefaceTextView}
import com.waz.ZLog.ImplicitTag._
import com.waz.model.ConvExpiry
import com.waz.utils.returning
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.conversation.ConversationController._
import com.waz.zclient.{FragmentHelper, R}

import scala.concurrent.duration._

class EphemeralOptionsFragment extends FragmentHelper {

  import com.waz.threading.Threading.Implicits.Ui

  private lazy val zms = inject[Signal[ZMessaging]]
  private lazy val convController = inject[ConversationController]

  private lazy val optionsList = returning(view[LinearLayout](R.id.list_view)) { _ =>
    convController.currentConv.map(_.ephemeralExpiration).map {
      case Some(ConvExpiry(e)) => Some(e)
      case _                   => None
    }.onUi(setNewValue)
  }

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle) = {
    inflater.inflate(R.layout.ephemeral_options_fragment, container, false)
  }

  private def setNewValue(e: Option[FiniteDuration]): Unit = {
    optionsList.foreach { v =>
      PredefinedExpirations.zipWithIndex.map { case (option, index) =>
        (option, v.getChildAt(index).asInstanceOf[LinearLayout]
          .getChildAt(0).asInstanceOf[LinearLayout])
      }.foreach { case (option, r) =>
        val textView = r.getChildAt(0).asInstanceOf[TypefaceTextView]
        val check = r.getChildAt(1).asInstanceOf[GlyphTextView]
        textView.setText(ConversationController.getEphemeralDisplayString(option))
        check.setVisible(e.equals(option))
        textView.onClick {
          for {
            z <- zms.head
            Some(convId) <- z.convsStats.selectedConversationId.head
            _ <- z.convsUi.setEphemeralGlobal(convId, option)
          } yield {}
        }
      }
    }
  }

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    optionsList.foreach { v =>
      PredefinedExpirations.foreach { _ =>
        getLayoutInflater.inflate(R.layout.conversation_option_item, v, true)
      }
    }
  }

  override def onResume() = {
    super.onResume()
  }

  override def onStop() = {
    super.onStop()
  }
}

object EphemeralOptionsFragment {
  val Tag = implicitLogTag
}

