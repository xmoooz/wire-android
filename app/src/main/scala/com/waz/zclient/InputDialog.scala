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
package com.waz.zclient

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.support.annotation.StringRes
import android.support.v4.app.DialogFragment
import android.support.v7.app.AlertDialog
import android.view.View
import android.view.View.OnAttachStateChangeListener
import android.widget.{EditText, TextView}
import com.waz.zclient.utils.RichTextView

object InputDialog {

  trait Event
  case class OnPositiveBtn(input: String) extends Event
  case object OnNegativeBtn               extends Event

  trait ValidatorResult
  object ValidatorResult {
    case object Valid extends ValidatorResult
    case class Invalid(actions: Option[EditText => Unit] = None) extends ValidatorResult
  }

  trait InputValidator {
    /**
      * @return Non empty option with error message if input is invalid.
      */
    def isInputInvalid(input: String): ValidatorResult
  }

  private val Title = "TITLE"
  private val Message = "MESSAGE"
  private val Input = "INPUT"
  private val InputHint = "INPUT_HINT"
  private val ValidateInput = "VALIDATE_INPUT"
  private val DisablePositiveBtnOnInvalidInput = "DISABLE_POSITIVE_BTN"
  private val NegativeBtn = "NEGATIVE_BTN"
  private val PositiveBtn = "POSITIVE_BTN"

  trait Listener {
    def onDialogEvent(event: Event): Unit
  }

  def newInstance(
                   @StringRes title: Int,
                   @StringRes message: Int,
                   inputValue: Option[String] = None,
                   @StringRes inputHint: Option[Int] = None,
                   validateInput: Boolean = false,
                   disablePositiveBtnOnInvalidInput: Boolean = false,
                   @StringRes negativeBtn: Int,
                   @StringRes positiveBtn: Int
                 ): InputDialog = {

    val dialog = new InputDialog()
    val bundle = new Bundle()

    bundle.putInt(Title, title)
    bundle.putInt(Message, message)
    inputValue.foreach(i => bundle.putString(Input, i))
    inputHint.foreach(ih => bundle.putInt(InputHint, ih))
    bundle.putBoolean(ValidateInput, validateInput)
    bundle.putBoolean(DisablePositiveBtnOnInvalidInput, disablePositiveBtnOnInvalidInput)
    bundle.putInt(NegativeBtn, negativeBtn)
    bundle.putInt(PositiveBtn, positiveBtn)

    dialog.setArguments(bundle)
    dialog
  }

}

class InputDialog extends DialogFragment {

  import InputDialog._

  private var listener: Option[Listener] = None
  private var validator: Option[InputValidator] = None

  def setListener(listener: Listener): this.type = {
    this.listener = Some(listener)
    this
  }

  def setValidator(validator: InputValidator): this.type = {
    this.validator = Some(validator)
    this
  }

  override def onCreateDialog(savedInstanceState: Bundle): Dialog = {
    super.onCreateDialog(savedInstanceState)
    val args = getArguments

    val view = getActivity.getLayoutInflater.inflate(R.layout.dialog_with_input_field, null)
    val message = view.findViewById[TextView](R.id.message)
    val input = view.findViewById[EditText](R.id.input)

    message.setText(args.getInt(Message))
    Option(args.getInt(InputHint)).foreach(input.setHint)

    if (savedInstanceState == null) {
      Option(args.getString(Input)).foreach(input.setText)
    }

    val dialog = new AlertDialog.Builder(getContext)
      .setTitle(args.getInt(Title))
      .setView(view)
      .setNegativeButton(args.getInt(NegativeBtn), new DialogInterface.OnClickListener {
        override def onClick(dialogInterface: DialogInterface, i: Int): Unit =
          listener.foreach(_.onDialogEvent(OnNegativeBtn))
      })
      .setPositiveButton(args.getInt(PositiveBtn), new DialogInterface.OnClickListener {
        override def onClick(dialogInterface: DialogInterface, i: Int): Unit =
          listener.foreach(_.onDialogEvent(OnPositiveBtn(input.getText.toString)))
      })
      .create()

    if (args.getBoolean(ValidateInput)) {
      val disablePositiveBtnOnInvalidInput = args.getBoolean(DisablePositiveBtnOnInvalidInput)
      lazy val positiveBtn = dialog.getButton(DialogInterface.BUTTON_POSITIVE)

      def validate(str: String): Unit = validator.foreach { inputValidator =>
        inputValidator.isInputInvalid(str) match {
          case ValidatorResult.Valid =>
            if (disablePositiveBtnOnInvalidInput) positiveBtn.setEnabled(true)
          case ValidatorResult.Invalid(actions) =>
            if (disablePositiveBtnOnInvalidInput) positiveBtn.setEnabled(false)
            actions.foreach(_(input))
        }
      }

      input.addOnAttachStateChangeListener(new OnAttachStateChangeListener {
        override def onViewDetachedFromWindow(v: View): Unit = {}
        override def onViewAttachedToWindow(v: View): Unit = validate(input.getText.toString)
      })
      input.addTextListener(str => validate(str))
    }

    dialog
  }

}
