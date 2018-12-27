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
package com.waz.zclient.messages

import java.util

import android.app.Activity
import android.content.Context
import android.support.v7.widget.RecyclerView.{OnScrollListener, ViewHolder}
import android.support.v7.widget.{DefaultItemAnimator, LinearLayoutManager, RecyclerView}
import android.util.AttributeSet
import android.view.WindowManager
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog._
import com.waz.utils._
import com.waz.api.Message
import com.waz.model.{ConvId, Dim2, MessageData}
import com.waz.service.assets2.AssetStatus
import com.waz.service.messages.MessageAndLikes
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, Signal}
import com.waz.zclient.common.controllers.AssetsController
import com.waz.zclient.controllers.navigation.{INavigationController, Page}
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.messages.MessageView.MsgBindOptions
import com.waz.zclient.messages.ScrollController.{BottomScroll, PositionScroll}
import com.waz.zclient.messages.controllers.MessageActionsController
import com.waz.zclient.ui.utils.KeyboardUtils
import com.waz.zclient.{Injectable, Injector, ViewHelper}

class MessagesListView(context: Context, attrs: AttributeSet, style: Int) extends RecyclerView(context, attrs, style) with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  import MessagesListView._

  val viewDim = Signal[Dim2]()
  val realViewHeight = Signal[Int]()
  val layoutManager = new MessagesListLayoutManager(context, LinearLayoutManager.VERTICAL, false)
  val adapter = new MessagesListAdapter(viewDim)
  val scrollController = new ScrollController(adapter, realViewHeight)

  val messagesController = inject[MessagesController]
  val messageActionsController = inject[MessageActionsController]

  viewDim.on(Threading.Ui){_ => adapter.notifyDataSetChanged()}

  messageActionsController.messageToReveal {
    case Some(messageData) =>
      adapter.positionForMessage(messageData).foreach { pos =>
        if (pos >= 0) {
          scrollController.targetPosition = Some(pos)
          scrollController.scrollToPositionRequested ! pos
          messageActionsController.messageToReveal ! None
        }
      } (Threading.Ui)
    case None =>
  }

  setHasFixedSize(true)
  setLayoutManager(layoutManager)
  setAdapter(adapter)
  setItemAnimator(new DefaultItemAnimator {
    // always reuse view holder, we will handle animations ourselves
    override def canReuseUpdatedViewHolder(viewHolder: ViewHolder, payloads: util.List[AnyRef]): Boolean = true
  })

  adapter.ephemeralCount { set =>
    val count = set.size
    Option(getContext).foreach {
      case a:Activity =>
        count match {
          case 0 => a.getWindow.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
          case _ => a.getWindow.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
      case _ => // not attahced, ignore
    }
  }

  scrollController.onScroll.on(Threading.Ui) { scroll =>

    def scrollCloseToTarget(target: Int, current: Int) =
      if (math.abs(current - target) > MaxSmoothScroll)
        layoutManager.scrollToPosition(if (target > current) target - MaxSmoothScroll else target + MaxSmoothScroll)

    verbose(s"Scrolling to: $scroll")

    scroll match {
      case BottomScroll(false) =>
        val target = adapter.getItemCount - 1
        layoutManager.snapToEnd()
        layoutManager.scrollToPosition(target)
        scrollController.onScrolled(target)
        verbose(s"Scrolling target: $target, item count: ${adapter.getItemCount}")

      case BottomScroll(true) =>
        val target = Math.max(0, adapter.getItemCount - 1)
        val current = layoutManager.findFirstVisibleItemPosition()
        layoutManager.snapToEnd()
        scrollCloseToTarget(target, current)
        smoothScrollToPosition(target)
        verbose(s"Scrolling target: $target, current: $current, item count: ${adapter.getItemCount}")

      case PositionScroll(pos, false) =>
        val target = Math.min(pos, adapter.getItemCount - 1)
        layoutManager.snapToStart()
        layoutManager.scrollToPosition(target)
        scrollController.onScrolled(target)
        verbose(s"Scrolling target: $target, item count: ${adapter.getItemCount}")

      case PositionScroll(pos, true) =>
        val current = layoutManager.findFirstVisibleItemPosition()
        layoutManager.snapToStart()
        scrollCloseToTarget(pos, current)
        smoothScrollToPosition(pos)
        verbose(s"Scrolling target: $pos, current: $current, item count: ${adapter.getItemCount}")
    }
  }

  addOnScrollListener(new OnScrollListener {
    override def onScrollStateChanged(recyclerView: RecyclerView, newState: Int): Unit = newState match {
      case RecyclerView.SCROLL_STATE_IDLE =>
        val page = inject[INavigationController].getCurrentPage
        if (page == Page.MESSAGE_STREAM) {
          scrollController.onScrolled(layoutManager.findLastVisibleItemPosition())
          messagesController.scrolledToBottom ! (layoutManager.findLastCompletelyVisibleItemPosition() == adapter.getItemCount - 1)
        } else {
          scrollController.onScrolledInvisible()
        }
      case RecyclerView.SCROLL_STATE_DRAGGING => {
        scrollController.onDragging()
        messagesController.scrolledToBottom ! false
        Option(getContext).map(_.asInstanceOf[Activity]).foreach(a => KeyboardUtils.hideKeyboard(a))
      }
      case _ =>
    }
  })

  override def onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int): Unit = {
    //We don't want the original height of the view to change if the keyboard comes up, or else images will be resized to
    //fit in the small space left. So only let the height change if for some reason the new height is bigger (shouldn't happen)
    //i.e., height in viewDim should always represent the height of the screen without the keyboard shown.
    viewDim.mutateOrDefault({ case Dim2(_, h) => Dim2(r - l, math.max(h, b - t)) }, Dim2(r - l, b - t))
    realViewHeight ! b - t
    super.onLayout(changed, l, t, r, b)
  }

  def scrollToBottom(): Unit = scrollController.onScrollToBottomRequested ! true
}

object MessagesListView {

  val MaxSmoothScroll = 50

  case class UnreadIndex(index: Int) extends AnyVal

  abstract class Adapter extends RecyclerView.Adapter[MessageViewHolder] {
    def getConvId: Option[ConvId]
    def getUnreadIndex: UnreadIndex
  }
}

case class MessageViewHolder(view: MessageView, adapter: MessagesListAdapter)(implicit ec: EventContext, inj: Injector) extends RecyclerView.ViewHolder(view) with Injectable {

  private val selection = inject[ConversationController].messages
  private val msgsController = inject[MessagesController]
  private lazy val assets = inject[AssetsController]

  val message = Signal[MessageData]
  def id = message.currentValue.map(_.id)

  private var opts = Option.empty[MsgBindOptions]
  private var _isFocused = false

  selection.focused.onChanged.on(Threading.Ui) { mId =>
    if (_isFocused != (id == mId)) adapter.notifyItemChanged(getAdapterPosition)
  }

  msgsController.lastSelfMessage.onChanged.on(Threading.Ui) { m =>
    opts foreach { o =>
      if (o.isLastSelf != id.contains(m.id)) adapter.notifyItemChanged(getAdapterPosition)
    }
  }

  msgsController.lastMessage.onChanged.on(Threading.Ui) { m =>
    opts foreach { o =>
      if (o.isLast != id.contains(m.id)) adapter.notifyItemChanged(getAdapterPosition)
    }
  }

  // mark message as read if message is bound while list is visible
  msgsController.fullyVisibleMessagesList.flatMap {
    case Some(convId) =>
      message.filter(_.convId == convId) flatMap {
        case msg if msg.isAssetMessage && msg.state == Message.Status.SENT =>
          // received asset message is considered read when its asset is available,
          // this is especially needed for ephemeral messages, only start the counter when message is downloaded
          msg.assetId.fold2(Signal.empty, assets.assetStatusSignal) flatMap {
            case (AssetStatus.Done, _) if msg.msgType == Message.Type.ASSET =>
              // image assets are considered read only once fully downloaded
              Signal const msg
//            case (_, AssetStatus.UPLOAD_DONE | AssetStatus.UPLOAD_CANCELLED | AssetStatus.UPLOAD_FAILED) if msg.msgType != Message.Type.ASSET =>
//              // for other assets it's enough when upload is done, download is user triggered here
//              Signal const msg
            case _ => Signal.empty[MessageData]
          }
        case msg => Signal const msg
      }
    case None => Signal.empty[MessageData]
  }(msgsController.onMessageRead)

  def bind(msg: MessageAndLikes, prev: Option[MessageData], next: Option[MessageData], opts: MsgBindOptions): Unit = {
    view.set(msg, prev, next, opts)
    message ! msg.message
    this.opts = Some(opts)
    _isFocused = selection.isFocused(msg.message.id)
  }
}
