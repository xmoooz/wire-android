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
    def isInputInvalid(dialogTag: String, input: String): ValidatorResult
  }

  private val Tittle = "TITTLE"
  private val Message = "MESSAGE"
  private val Input = "INPUT"
  private val InputHint = "INPUT_HINT"
  private val ValidateInput = "VALIDATE_INPUT"
  private val DisablePositiveBtnOnInvalidInput = "DISABLE_POSITIVE_BTN"
  private val NegativeBtn = "NEGATIVE_BTN"
  private val PositiveBtn = "POSITIVE_BTN"

  trait Listener {
    def onDialogEvent(dialogTag: String, event: Event): Unit
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

    bundle.putInt(Tittle, title)
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

  private def listener: Listener = (getParentFragment, getActivity) match {
    case (l: Listener, _) => l
    case (_, l: Listener) => l
    case _ =>
      throw new RuntimeException("Parent fragment or activity do not implement Listener trait")
  }

  private def inputValidator: InputValidator = (getParentFragment, getActivity) match {
    case (iv: InputValidator, _) => iv
    case (_, iv: InputValidator) => iv
    case _ =>
      throw new RuntimeException("Parent fragment or activity do not implement InputValidator trait")
  }

  override def onCreateDialog(savedInstanceState: Bundle): Dialog = {
    super.onCreateDialog(savedInstanceState)
    val args = getArguments

    val view = getActivity.getLayoutInflater.inflate(R.layout.dialog_with_input_field, null)
    val message = view.findViewById[TextView](R.id.message)
    val input = view.findViewById[EditText](R.id.input)

    message.setText(args.getInt(Message))
    Option(args.getString(InputHint)).foreach(input.setHint)

    if (savedInstanceState == null) {
      Option(args.getString(Input)).foreach(input.setText)
    }

    val dialog = new AlertDialog.Builder(getContext)
      .setTitle(args.getInt(Tittle))
      .setView(view)
      .setNegativeButton(args.getInt(NegativeBtn), new DialogInterface.OnClickListener {
        override def onClick(dialogInterface: DialogInterface, i: Int): Unit =
          listener.onDialogEvent(getTag, OnNegativeBtn)
      })
      .setPositiveButton(args.getInt(PositiveBtn), new DialogInterface.OnClickListener {
        override def onClick(dialogInterface: DialogInterface, i: Int): Unit =
          listener.onDialogEvent(getTag, OnPositiveBtn(input.getText.toString))
      })
      .create()

    if (args.getBoolean(ValidateInput)) {
      val disablePositiveBtnOnInvalidInput = args.getBoolean(DisablePositiveBtnOnInvalidInput)
      lazy val positiveBtn = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
      input.addTextListener { str =>
        inputValidator.isInputInvalid(getTag, str) match {
          case ValidatorResult.Valid =>
            if (disablePositiveBtnOnInvalidInput) positiveBtn.setEnabled(true)
          case ValidatorResult.Invalid(actions) =>
            if (disablePositiveBtnOnInvalidInput) positiveBtn.setEnabled(false)
            actions.foreach(_(input))
        }
      }
    }

    dialog
  }

}
