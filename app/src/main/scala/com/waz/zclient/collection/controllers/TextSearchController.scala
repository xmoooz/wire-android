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
package com.waz.zclient.collection.controllers

import com.waz.ZLog.ImplicitTag.implicitLogTag
import com.waz.api.ContentSearchQuery
import com.waz.model.{ConvId, MessageContentIndexDao, MessageData, MessageId}
import com.waz.service.ZMessaging
import com.waz.threading.Threading.Implicits.Background
import com.waz.utils.events._
import com.waz.utils.wrappers.DBCursor
import com.waz.zclient.collection.SearchTextDataSourceFactory
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.messages.PagedListWrapper
import com.waz.zclient.{Injectable, Injector}

import scala.concurrent.Future

class TextSearchController()(implicit inj: Injector, ec: EventContext) extends Injectable  {
  private val zms = inject[Signal[ZMessaging]]
  private val convController = inject[ConversationController]
  private val storage = zms.map(_.storage.db)

  private val dataSourceFactory = new SearchTextDataSourceFactory

  val searchQuery: SourceSignal[ContentSearchQuery] = Signal[ContentSearchQuery](ContentSearchQuery.empty)

  private def loadCursor(convId: ConvId, query: ContentSearchQuery): Future[Option[DBCursor]] =
    storage.head.flatMap(_.read(implicit db => MessageContentIndexDao.findContent(query, Some(convId)))).map { Option(_) }

  private def convChanged(zms: ZMessaging, convId: ConvId): EventStream[Seq[MessageId]] =
    EventStream.union(
      zms.messagesStorage.onChanged.filter(_.exists(_.convId == convId)).map(_.map(_.id)),
      zms.messagesStorage.onDeleted)

  lazy val pagedListData: Signal[(PagedListWrapper[MessageData], ContentSearchQuery)] = for {
    z      <- zms
    cId    <- convController.currentConv.map(_.id)
    query  <- searchQuery
    cursor <- RefreshingSignal[Option[DBCursor], Seq[MessageId]](loadCursor(cId, query), convChanged(z, cId))
    list   = PagedListWrapper(dataSourceFactory.getPagedList(cursor))
  } yield (list, query)

  val matchingTextSearchMessages: Signal[Set[MessageId]] = for {
    z <- zms
    convId <- convController.currentConvId
    query <- searchQuery
    res <- if (query.isEmpty) Signal.const(Set.empty[MessageId])
    else Signal future z.messagesIndexStorage.matchingMessages(query, Some(convId))
  } yield res
}
