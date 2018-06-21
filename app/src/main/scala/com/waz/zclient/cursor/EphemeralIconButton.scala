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
package com.waz.zclient.cursor

import android.content.Context
import android.content.res.ColorStateList
import android.util.{AttributeSet, TypedValue}
import android.view.Gravity
import com.waz.model.ConvExpiry
import com.waz.utils.events.Signal
import com.waz.zclient.R
import com.waz.zclient.cursor.CursorController.KeyboardState
import com.waz.zclient.pages.extendedcursor.ExtendedCursorContainer
import com.waz.zclient.ui.utils._
import com.waz.zclient.ui.views.OnDoubleClickListener
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils._

class EphemeralIconButton(context: Context, attrs: AttributeSet, defStyleAttr: Int) extends CursorIconButton(context, attrs, defStyleAttr) { view =>
  def this(context: Context, attrs: AttributeSet) { this(context, attrs, 0) }
  def this(context: Context) { this(context, null) }

  import java.util.concurrent.TimeUnit.{DAYS, MINUTES}

  val timeString = controller.conv.map(_.ephemeralExpiration.map(_.duration)).map { expiration =>
    expiration.map { duration =>
      duration.unit match {
        case DAYS =>
          getString(R.string.cursor__ephemeral_message__timer_days, duration.toDays.toString)
        case MINUTES =>
          getString(R.string.cursor__ephemeral_message__timer_min, duration.toMinutes.toString)
        case _ =>
          getString(R.string.cursor__ephemeral_message__timer_seconds, duration.toSeconds.toString)
      }
    }
  }

  override val buttonColor =
    controller.ephemeralExp.flatMap {
      case Some(ConvExpiry(_)) => Signal.const(ColorStateList.valueOf(R.color.graphite))
      case Some(_)             => accentColor.map(_.getColor).map(ColorStateList.valueOf)
      case _                   => defaultColor
    }

  val typeface = controller.isEphemeral.map {
    case true => TypefaceUtils.getTypeface(getString(R.string.wire__typeface__regular))
    case false => TypefaceUtils.getTypeface(TypefaceUtils.getGlyphsTypefaceName)
  }

  val textSize = controller.isEphemeral.map {
    case true => getDimenPx(R.dimen.wire__text_size__small)
    case false => getDimenPx(R.dimen.wire__text_size__regular)
  }

  override val glyph = Signal[Int]()

  override val background = controller.ephemeralExp.flatMap {
    case Some(exp) =>
      (exp match {
        case ConvExpiry(_) => Signal.const(R.color.graphite)
        case _             => accentColor.map(_.getColor)
      }).map { c =>
        val bgColor = ColorUtils.injectAlpha(ResourceUtils.getResourceFloat(getResources, R.dimen.ephemeral__accent__timer_alpha), c)
        ColorUtils.getTintedDrawable(getContext, R.drawable.background__cursor__ephemeral_timer, bgColor)
      }
    case _ => defaultBackground
  }

  override def onFinishInflate(): Unit = {
    super.onFinishInflate()

    setGravity(Gravity.CENTER)

    typeface.onUi(setTypeface)
    textSize.onUi(setTextSize(TypedValue.COMPLEX_UNIT_PX, _))

    timeString.onUi {
      case Some(t) => setText(t)
      case _       => setText(R.string.glyph__hourglass)
    }

    controller.ephemeralBtnVisible.onUi(view.setVisible)
  }

  setOnClickListener(new OnDoubleClickListener() {
    override def onDoubleClick(): Unit =
      controller.toggleEphemeralMode()

    override def onSingleClick(): Unit = {
      controller.keyboard ! KeyboardState.ExtendedCursor(ExtendedCursorContainer.Type.EPHEMERAL)
    }
  })
}
