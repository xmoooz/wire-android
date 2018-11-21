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
package com.waz.zclient.assets2

import java.io.InputStream
import java.net.URI

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.waz.model.Mime
import com.waz.service.assets2.UriHelper

import scala.util.Try

class AndroidUriHelper(context: Context) extends UriHelper {

  private def androidUri(uri: URI): Uri = Uri.parse(uri.toString)

  override def openInputStream(uri: URI): Try[InputStream] = Try {
    context.getContentResolver.openInputStream(androidUri(uri))
  }

  override def extractMime(uri: URI): Try[Mime] = Try {
    val mimeStr = context.getContentResolver.getType(androidUri(uri))
    Mime(mimeStr)
  }

  override def extractSize(uri: URI): Try[Long] = Try {
    context.getContentResolver.openAssetFileDescriptor(androidUri(uri), "r").getDeclaredLength
  }

  override def extractFileName(uri: URI): Try[String] = Try {
    val cursor = context.getContentResolver.query(androidUri(uri), null, null, null, null)
    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
    cursor.moveToFirst()
    val fileName = cursor.getString(nameIndex)
    cursor.close()
    fileName
  }

}
