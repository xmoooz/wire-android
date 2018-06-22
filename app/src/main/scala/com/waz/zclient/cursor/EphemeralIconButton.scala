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
import android.graphics.drawable.Drawable
import android.util.{AttributeSet, TypedValue}
import android.view.Gravity
import com.waz.model.{ConvExpiry, MessageExpiry}
import com.waz.utils.events.Signal
import com.waz.zclient.R
import com.waz.zclient.cursor.CursorController.KeyboardState
import com.waz.zclient.pages.extendedcursor.ExtendedCursorContainer
import com.waz.zclient.paintcode._
import com.waz.zclient.ui.utils._
import com.waz.zclient.ui.views.OnDoubleClickListener
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils._

class EphemeralIconButton(context: Context, attrs: AttributeSet, defStyleAttr: Int) extends CursorIconButton(context, attrs, defStyleAttr) { view =>
  def this(context: Context, attrs: AttributeSet) { this(context, attrs, 0) }
  def this(context: Context) { this(context, null) }

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


  val unitAndValue = controller.conv.map(_.ephemeralExpiration.map(_.display))

  val display = unitAndValue.map(_.map(_._1.toString)).map {
    case Some(t) => t
    case None    => getString(R.string.glyph__hourglass)
  }

  //For QA testing
  val contentDescription = unitAndValue.map {
    case Some((l, unit)) => s"$l$unit"
    case None => "off"
  }

  override val glyph = Signal[Int]()

  override val background: Signal[Drawable] = controller.ephemeralExp.flatMap {
    case Some(exp) =>
      (exp match {
        case ConvExpiry(d)    => Signal.const((exp.display._2, getColor(R.color.graphite)))
        case MessageExpiry(d) => accentColor.map(_.getColor).map((exp.display._2, _))
      }).map { case (unit, color) =>
        import com.waz.model.EphemeralDuration._
        unit match {
          case Second => EphemeralSecondIcon(color)
          case Minute => EphemeralMinuteIcon(color)
          case Hour   => EphemeralHourIcon(color)
          case Day    => EphemeralDayIcon(color)
          case Week   => EphemeralWeekIcon(color)
        }
      }
    case _ => defaultBackground
  }

  override def onFinishInflate(): Unit = {
    super.onFinishInflate()

    setGravity(Gravity.CENTER)

    typeface.onUi(setTypeface)
    textSize.onUi(setTextSize(TypedValue.COMPLEX_UNIT_PX, _))

    display.onUi(setText)
    contentDescription.onUi(setContentDescription)

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
