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

import java.io.{ByteArrayInputStream, InputStream}
import java.net.URI

import android.content.Context
import android.media.{MediaDataSource, MediaExtractor, MediaMetadataRetriever}
import android.net.Uri
import com.waz.utils._

import scala.util.Try

object MetadataExtractionUtils {

  type Source = Either[URI, MediaDataSource]

  class BytesMediaDataSource(bytes: Array[Byte]) extends MediaDataSource {
    private val is: ByteArrayInputStream = new ByteArrayInputStream(bytes)

    override def readAt(position: Long, buffer: Array[Byte], offset: Int, size: Int): Int = {
      is.skip(position)
      is.read(buffer, offset, size)
    }

    override def getSize: Long =
      bytes.length

    override def close(): Unit =
      is.close()
  }

  class InputStreamMediaDataSource(is: InputStream) extends MediaDataSource {

    override def readAt(position: Long, buffer: Array[Byte], offset: Int, size: Int): Int = {
      is.skip(position)
      is.read(buffer, offset, size)
    }

    override def getSize: Long = -1

    override def close(): Unit =
      is.close()
  }

  implicit val RetrieverCleanup: Cleanup[MediaMetadataRetriever] =
    new Cleanup[MediaMetadataRetriever] {
      override def apply(a: MediaMetadataRetriever): Unit = a.release()
    }

  implicit val ExtractorCleanup: Cleanup[MediaExtractor] =
    new Cleanup[MediaExtractor] {
      override def apply(a: MediaExtractor): Unit = a.release()
    }

  def createMetadataRetriever(source: Source)(implicit c: Context): Managed[MediaMetadataRetriever] =
    Managed(new MediaMetadataRetriever).map { retriever =>
      source match {
        case Left(uri) => retriever.setDataSource(c, Uri.parse(uri.toString))
        case Right(mediaDataSource) => retriever.setDataSource(mediaDataSource)
      }
      retriever
    }

  def createMediaExtractor(source: Source)(implicit c: Context): Managed[MediaExtractor] =
    Managed(new MediaExtractor).map { extractor =>
      source match {
        case Left(uri) => extractor.setDataSource(c, Uri.parse(uri.toString), null)
        case Right(mediaDataSource) => extractor.setDataSource(mediaDataSource)
      }
      extractor
    }

  def retrieve[A](key: Int, tag: String, convert: String => A)
                 (implicit retriever: MediaMetadataRetriever): Either[String, A] =
    for {
      s <- Option(retriever.extractMetadata(key)).toRight(s"$tag ($key) is null")
      result <- Try(convert(s)).toRight(t => s"unable to convert $tag ($key) of value '$s': ${t.getMessage}")
    } yield result

}
