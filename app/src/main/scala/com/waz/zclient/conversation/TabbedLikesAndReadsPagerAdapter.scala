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
package com.waz.zclient.conversation

import android.content.Context
import android.support.v4.view.PagerAdapter
import android.support.v7.widget.{LinearLayoutManager, RecyclerView}
import android.view.{View, ViewGroup}
import com.waz.model.{MessageId, UserData, UserId}
import com.waz.utils.events.{EventContext, Signal}
import com.waz.utils.returning
import com.waz.zclient.participants.ParticipantsAdapter
import com.waz.zclient.{Injectable, Injector}

class TabbedLikesAndReadsPagerAdapter(msgId: MessageId, likes: Signal[Seq[UserId]], reads: Signal[Seq[UserId]])(implicit context: Context, injector: Injector, eventContext: EventContext) extends PagerAdapter with Injectable {
  import TabbedLikesAndReadsPagerAdapter._

  private val likesAdapter = new ParticipantsAdapter(likes, showPeopleOnly = true, showArrow = false)
  private val readsAdapter = new ParticipantsAdapter(reads, createSubtitle = Some(createSubtitle(msgId, _)), showPeopleOnly = true, showArrow = false)

  private def recyclerView(adapter: RecyclerView.Adapter[_ <: RecyclerView.ViewHolder]) =
    returning( new RecyclerView(context) ) { rv =>
      rv.setLayoutManager(new LinearLayoutManager(context))
      rv.setHasFixedSize(true)
      rv.setAdapter(adapter)
      rv.setClipToPadding(false)
    }

  override def instantiateItem(container: ViewGroup, position: Int): java.lang.Object = returning(
    tabs(position) match {
      case 'reads => recyclerView(readsAdapter)
      case 'likes => recyclerView(likesAdapter)
      case _ => throw new RuntimeException("Unexpected ViewPager position")
    }
  ) { view => container.addView(view) }

  override def destroyItem(container: ViewGroup, position: Int, view: Any): Unit =
    container.removeView(view.asInstanceOf[View])

  override def getCount: Int = tabs.length

  override def isViewFromObject(view: View, obj: Any): Boolean = view == obj

  override def getPageTitle(pos: Int): CharSequence = "" // page titles are set in LikesAndReadsFragment
}

object TabbedLikesAndReadsPagerAdapter {
  val tabs = List('reads, 'likes)

  def createSubtitle(msgId: MessageId, user: UserData): String = {
    "READ RECEIPT TIMESTAMP" // TODO
  }
}
