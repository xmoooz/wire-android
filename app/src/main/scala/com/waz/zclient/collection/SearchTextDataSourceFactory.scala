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

import android.arch.paging.PagedList
import com.waz.model.MessageData
import com.waz.threading.Threading
import com.waz.utils.returning
import com.waz.utils.wrappers.DBCursor
import com.waz.zclient.Injector
import com.waz.zclient.MessageAndLikesSourceFactory._
import com.waz.zclient.messages.ExecutorWrapper

class SearchTextDataSourceFactory(settings: DataSourceSettings = DefaultSettings)(implicit val injector: Injector) {

  @volatile private var _pagedList = Option.empty[PagedList[MessageData]]
  def getPagedList(cursor: Option[DBCursor]): PagedList[MessageData] = {
    _pagedList.foreach(_.getDataSource.invalidate())

    val config = new PagedList.Config.Builder()
      .setPageSize(settings.pageSize)
      .setInitialLoadSizeHint(settings.initialLoadSizeHint)
      .setEnablePlaceholders(true)
      .setPrefetchDistance(settings.prefetchDistance)
      .build()

    returning(new PagedList.Builder[Integer, MessageData](new TextSearchDataSource(cursor), config)
      .setFetchExecutor(ExecutorWrapper(Threading.Background))
      .setNotifyExecutor(ExecutorWrapper(Threading.Ui))
      .build()) { pl => _pagedList = Option(pl) }
  }
}

