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
import android.support.v7.view.ContextThemeWrapper
import android.util.AttributeSet
import android.view.View
import android.widget.{LinearLayout, NumberPicker}
import com.waz.ZLog
import com.waz.ZLog.ImplicitTag._
import com.waz.utils.events.{EventStream, Subscription}
import com.waz.zclient.{R, ViewHelper}
import com.waz.zclient.utils.ContextUtils._

import scala.concurrent.duration.{FiniteDuration, _}

class EphemeralLayout(context: Context, attrs: AttributeSet, defStyleAttr: Int) extends LinearLayout(context, attrs, defStyleAttr) with ViewHelper {
  import EphemeralLayout._

  def this(context: Context, attrs: AttributeSet) { this(context, attrs, 0) }
  def this(context: Context) { this(context, null) }

  lazy val numberPicker = new NumberPicker(new ContextThemeWrapper(getContext, R.style.NumberPickerText))

  val expirationSelected = EventStream[(Option[FiniteDuration], Boolean)]()

  def setSelectedExpiration(expiration: Option[FiniteDuration]): Unit =
    numberPicker.setValue(PredefinedExpirations.indexWhere(_ == expiration))

  override protected def onFinishInflate(): Unit = {
    super.onFinishInflate()
    numberPicker.setMinValue(0)
    numberPicker.setMaxValue(PredefinedExpirations.size - 1)
    numberPicker.setDisplayedValues(PredefinedExpirations.map(getEphemeralString).toArray)
    numberPicker.setOnClickListener(new View.OnClickListener() {
      override def onClick(v: View): Unit =
        expirationSelected ! (PredefinedExpirations(numberPicker.getValue), true)
    })
    numberPicker.setOnValueChangedListener(new NumberPicker.OnValueChangeListener() {
      override def onValueChange(picker: NumberPicker, oldVal: Int, newVal: Int): Unit = {
        expirationSelected ! (PredefinedExpirations(numberPicker.getValue), false)
      }
    })

    try {
      val f = numberPicker.getClass.getDeclaredField("mSelectionDivider") //NoSuchFieldException
      f.setAccessible(true)
      f.set(numberPicker, getDrawable(R.drawable.number_picker_divider))
    } catch {
      case t: Throwable =>
        ZLog.error("Something went wrong", t)
    }
    addView(numberPicker)
  }


  private var subscription = Option.empty[Subscription]
  def setCallback(cb: Callback): Unit = {
    subscription.foreach(_.destroy())
    subscription = Some(expirationSelected.onUi {
      case (exp, close) => cb.onEphemeralExpirationSelected(exp, close)
    })
  }
}

object EphemeralLayout {

  trait Callback {
    def onEphemeralExpirationSelected(expiration: Option[FiniteDuration], close: Boolean): Unit
  }

  lazy val PredefinedExpirations = Seq(
    None,
    Some(10.seconds),
    Some(5.minutes),
    Some(1.hour),
    Some(1.day),
    Some(7.days),
    Some(28.days)
  )

  //TODO - find a way to convert these to strings in a less repetitive manner
  def getEphemeralString(exp: Option[FiniteDuration])(implicit context: Context) =
    exp.toString
//    exp match {
//      case None                        => getString(R.string.ephemeral_message__timeout__off)
//      case Some(Duration(5, SECONDS))  => getString(R.string.ephemeral_message__timeout__5_sec)
//      case Some(Duration(10, SECONDS)) => getString(R.string.ephemeral_message__timeout__15_sec)
//      case Some(Duration(30, SECONDS)) => getString(R.string.ephemeral_message__timeout__30_sec)
//      case Some(Duration(1, MINUTES))  => getString(R.string.ephemeral_message__timeout__1_min)
//      case Some(Duration(5, MINUTES))  => getString(R.string.ephemeral_message__timeout__5_min)
//      case Some(Duration(1, DAYS))     => getString(R.string.ephemeral_message__timeout__1_day)
//      case Some(d)                     => d.toString()
//    }
}
