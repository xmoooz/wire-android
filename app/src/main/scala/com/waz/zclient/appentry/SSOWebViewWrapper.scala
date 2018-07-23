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

import android.webkit.{WebView, WebViewClient}
import com.waz.ZLog._
import com.waz.ZLog.ImplicitTag._
import com.waz.model.UserId
import com.waz.sync.client.AuthenticationManager.Cookie
import com.waz.utils.events.EventStream
import com.waz.utils.wrappers.URI
import com.waz.zclient.appentry.SSOWebViewWrapper._

import scala.concurrent.{Future, Promise}
import scala.util.Success


class SSOWebViewWrapper(webView: WebView, backendHost: String) {

  private var loginPromise = Promise[SSOResponse]()

  val onTitleChanged = EventStream[String]()
  val onUrlChanged = EventStream[String]()

  webView.getSettings.setJavaScriptEnabled(true)

  webView.setWebViewClient(new WebViewClient {
    override def onPageFinished(view: WebView, url: String): Unit = {
      onTitleChanged ! {
        val title = view.getTitle
        Option(URI.parse(title).getHost).filter(_.nonEmpty).getOrElse(title)
      }
      onUrlChanged ! url

      parseURL(url).foreach { result =>
        loginPromise.tryComplete(Success(result))
      }
      verbose(s"onPageFinished: $url")
    }
  })

  def loginWithCode(code: String): Future[SSOResponse] = {
    loginPromise.tryComplete(Success(Left("cancelled")))
    loginPromise = Promise[SSOResponse]()

    val url = URI.parse(s"$backendHost/${InitiateLoginPath(code)}")
      .buildUpon
      .appendQueryParameter("success_redirect", "wire://localhost/?$cookie&user=$userid")
      .appendQueryParameter("error_redirect", "wire://localhost/?$label")
      .build
      .toString

    webView.loadUrl(url)
    loginPromise.future
  }

  protected def parseURL(url: String): Option[SSOResponse] = {
    val uri = URI.parse(url)
    val cookie = Option(uri.getQueryParameter("cookie"))
    val userId = Option(uri.getQueryParameter("user"))
    val failure = Option(uri.getQueryParameter("failure"))

    (cookie, userId, failure) match {
      case (Some(c), Some(uId), _) => Some(Right(Cookie(c), UserId(uId)))
      case (_, _, Some(f)) => Some(Left(f))
      case _ => None
    }
  }

}

object SSOWebViewWrapper {

  type SSOResponse = Either[String, (Cookie, UserId)]
  def InitiateLoginPath(code: String) = s"sso/initiate-login/$code"
}
