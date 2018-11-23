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
package com.waz.zclient.collection.adapters

import android.arch.paging.PagedListAdapter
import android.content.Context
import android.support.v7.util.DiffUtil
import android.support.v7.widget.RecyclerView
import android.view.ViewGroup
import com.waz.api.ContentSearchQuery
import com.waz.model.MessageData
import com.waz.utils.events.EventContext
import com.waz.zclient.collection.adapters.SearchAdapter._
import com.waz.zclient.usersearch.views.TextSearchResultRowView
import com.waz.zclient.{Injectable, Injector}

class SearchAdapter()(implicit context: Context, injector: Injector, eventContext: EventContext) extends PagedListAdapter[MessageData, SearchResultRowViewHolder](MessageDataDiffCallback) with Injectable { adapter =>

  var contentSearchQuery = ContentSearchQuery.empty

  override def onBindViewHolder(holder: SearchResultRowViewHolder, position: Int): Unit = {
    Option(getItem(position)).foreach(m => holder.set(m, contentSearchQuery))
  }

  override def onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchResultRowViewHolder = {
    new SearchResultRowViewHolder(new TextSearchResultRowView(context))
  }
}

object SearchAdapter {
  val MessageDataDiffCallback: DiffUtil.ItemCallback[MessageData] = new DiffUtil.ItemCallback[MessageData] {
    override def areItemsTheSame(o: MessageData, n: MessageData): Boolean = n.id == o.id
    override def areContentsTheSame(o: MessageData, n: MessageData): Boolean = areItemsTheSame(o, n)
  }
}

class SearchResultRowViewHolder(view: TextSearchResultRowView)(implicit eventContext: EventContext) extends RecyclerView.ViewHolder(view){

  def set(message: MessageData, contentSearchQuery: ContentSearchQuery): Unit = view.set(message, contentSearchQuery)
}
