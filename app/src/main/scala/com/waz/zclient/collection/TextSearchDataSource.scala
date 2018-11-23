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
package com.waz.zclient.collection

import android.arch.paging.PositionalDataSource
import com.waz.ZLog.ImplicitTag.implicitLogTag
import com.waz.ZLog.error
import com.waz.content.MessagesStorage
import com.waz.db.Reader
import com.waz.model._
import com.waz.threading.Threading.Implicits.Background
import com.waz.utils.events.Signal
import com.waz.utils.wrappers.DBCursor
import com.waz.zclient.{Injectable, Injector}

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}

class TextSearchDataSource(val cursor: Option[DBCursor])(implicit inj: Injector) extends PositionalDataSource[MessageData] with Injectable {

  private val messagesStorage = inject[Signal[MessagesStorage]]

  private def load(start: Int, count: Int): Future[Seq[MessageData]] = cursor match {
    case Some(c) =>
      var msgData: Seq[MessageContentIndexEntry] = Nil
      synchronized {
        val totalCount = c.getCount
        msgData = (start until (start + count)).flatMap { pos =>
          if (pos < totalCount && c.moveToPosition(pos)) {
            List(MessageContentIndexDao(c))
          }
          else
            Nil
        }
      }
      messagesStorage.head.flatMap(_.getMessages(msgData.map(_.messageId):_*).map(_.flatten))
    case _ => Future.successful(Nil)
  }

  override def loadInitial(params: PositionalDataSource.LoadInitialParams, callback: PositionalDataSource.LoadInitialCallback[MessageData]): Unit = {
    val total = totalCount
    val start = PositionalDataSource.computeInitialLoadPosition(params, total)
    val size = PositionalDataSource.computeInitialLoadSize(params, start, total)

    try {
      val data = Await.result(load(start, size), 5.seconds)
      callback.onResult(data.asJava, start, total)
    } catch {
      case _: Throwable =>
        load(start, size).foreach(data => callback.onResult(data.asJava, start, total))
    }
  }

  override def loadRange(params: PositionalDataSource.LoadRangeParams, callback: PositionalDataSource.LoadRangeCallback[MessageData]): Unit = {
    load(params.startPosition, params.loadSize).onComplete {
      case Success(data) =>
        callback.onResult(data.asJava)
      case Failure(e) =>
        error(e.getMessage)
    }
  }

  def totalCount: Int = cursor.map(_.getCount).getOrElse(0)

  override def invalidate(): Unit = {
    cursor.foreach(_.close())
    super.invalidate()
  }
}

object TextSearchDataSource {
  object TextMessageIndexEntry {
    def apply(cursor: DBCursor): MessageContentIndexEntry = MessageContentIndexDao(cursor)
  }
  implicit object TextMessageIndexEntryReader extends Reader[MessageContentIndexEntry] {
    override def apply(implicit c: DBCursor): MessageContentIndexEntry = TextMessageIndexEntry(c)
  }
}
