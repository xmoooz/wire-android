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
package com.waz.zclient.common.controllers

import android.content.Context
import android.content.res.Resources
import android.util.AttributeSet
import android.view.{View, ViewGroup}
import android.widget.FrameLayout
import com.waz.ZLog.ImplicitTag._
import com.waz.content.UserPreferences.DarkTheme
import com.waz.service.AccountManager
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, Signal, SourceSignal}
import com.waz.utils.returning
import com.waz.zclient.common.controllers.ThemeController.Theme
import com.waz.zclient.ui.theme.{OptionsDarkTheme, OptionsLightTheme, OptionsTheme}
import com.waz.zclient.{Injectable, Injector, R, ViewHelper}

import scala.concurrent.Await
import scala.concurrent.duration._

class ThemeController(implicit injector: Injector, context: Context, ec: EventContext) extends Injectable {
  private val am = inject[Signal[AccountManager]]

  import Threading.Implicits.Background

  val optionsDarkTheme:  OptionsTheme = new OptionsDarkTheme(context)
  val optionsLightTheme: OptionsTheme = new OptionsLightTheme(context)

  val darkThemePref = am.map(_.userPrefs.preference(DarkTheme))

  val darkThemeSet = darkThemePref.flatMap(_.signal).disableAutowiring()

  def setDarkTheme(active: Boolean) =
    darkThemePref.head.flatMap(_ := active)

  def toggleDarkTheme() =
    darkThemePref.head.flatMap(_.mutate(!_))

  def isDarkTheme: Boolean = darkThemeSet.currentValue.contains(true)

  def forceLoadDarkTheme: Int = {
    val set = try {
      Await.result(darkThemeSet.head, 1.seconds)
    } catch {
      case _: Exception => false
    }
    if (set) R.style.Theme_Dark else R.style.Theme_Light
  }

  def getTheme: Int = if (isDarkTheme) R.style.Theme_Dark else R.style.Theme_Light

  def getThemeDependentOptionsTheme: OptionsTheme = if (isDarkTheme) optionsDarkTheme else optionsLightTheme

  lazy val darkTheme = returning(context.getResources.newTheme())(_.applyStyle(R.style.Theme_Dark, true))
  lazy val lightTheme = returning(context.getResources.newTheme())(_.applyStyle(R.style.Theme_Light, true))

  val currentTheme = darkThemeSet.map{ dt => if(dt) Theme.Dark else Theme.Light }

  def getTheme(theme: Theme): Resources#Theme = {
    theme match {
      case Theme.Light => lightTheme
      case Theme.Dark => darkTheme
    }
  }
}

trait ThemeControllingView extends View with ViewHelper {
  val theme: SourceSignal[Option[Theme]] = Signal(None)

  theme.onUi{ theme =>
    (this, theme) match {
      case (vg: ViewGroup, Some(t)) => ThemedView.dispatchSetTheme(vg, t)
      case _ =>
    }

  }
}

class ThemeControllingFrameLayout(context: Context, attrs: AttributeSet, defStyle: Int) extends FrameLayout(context, attrs, defStyle) with ThemeControllingView {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)
}

trait ThemedView extends View {

  //TODO: Replace with a signal
  var currentTheme: Option[Theme] = None

  def setTheme(theme: Theme): Unit

  override def onAttachedToWindow(): Unit = {
    super.onAttachedToWindow()
    currentTheme = getThemeFromParent(this)
    currentTheme.foreach(setTheme)
  }

  private def getThemeFromParent(view: View): Option[Theme] = {
    view.getParent match {
      case v: ThemeControllingView => v.theme.currentValue.flatten
      case v: ThemedView if v.currentTheme.isDefined => v.currentTheme
      case v: View => getThemeFromParent(v)
      case _ => None
    }
  }
}

object ThemedView {
  def dispatchSetTheme(viewGroup: ViewGroup, theme: Theme): Unit = {

    def applyTheme(view: View) = view match {
      case tv: ThemedView =>
        tv.currentTheme = Some(theme)
        tv.setTheme(theme)
      case _ =>
    }

    (0 until viewGroup.getChildCount).map(viewGroup.getChildAt(_)).foreach { view =>
      applyTheme(view)
      view match {
        case vg: ViewGroup => dispatchSetTheme(vg, theme)
        case _ =>
      }
    }
    applyTheme(viewGroup)
  }
}

object ThemeController {
  object Theme extends Enumeration {
    val Light, Dark = Value
  }
  type Theme = Theme.Value
}
