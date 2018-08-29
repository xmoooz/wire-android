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
package com.waz.zclient.common.controllers

import android.content.Context
import android.net.Uri
import android.os.Vibrator
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog.verbose
import com.waz.content.UserPreferences
import com.waz.media.manager.MediaManager
import com.waz.media.manager.context.IntensityLevel
import com.waz.model.UserId
import com.waz.service.{AccountsService, ZMessaging}
import com.waz.threading.{DispatchQueue, Threading}
import com.waz.utils.events.{EventContext, Signal}
import com.waz.zclient.common.controllers.SoundController.Sound
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.DeprecationUtils
import com.waz.zclient.{R, _}

trait SoundController {
  def getCurrentSettings(userId: UserId): Option[SoundController.SoundSettings]
  def play(userId: UserId, sound: Sound): Unit
  def stop(userId: UserId, sound: Sound): Unit
}

object SoundController {
  sealed trait Sound
  object Sound {
    case object IncomingCallRingtone extends Sound
    case object IncomingCallRingtoneInCall extends Sound
    case object OutgoingCallRingtone extends Sound
    case object OutgoingVideoCallRingtone extends Sound

    case object PingFromMe extends Sound
    case object PingFromThem extends Sound

    case object FirstIncomingMessage extends Sound
    case object IncomingMessage extends Sound

    case object CallEstablished extends Sound
    case object CallEnded extends Sound
    case object CallDropped extends Sound
    case object Alert extends Sound
    case object CameraShutter extends Sound
  }

  case class SoundSettings(soundIntensity: Option[IntensityLevel],
                           ringTone: Option[Uri],
                           textTone: Option[Uri],
                           pingTone: Option[Uri])
}

class SoundControllerImpl(implicit inj: Injector, cxt: Context) extends SoundController with Injectable {
  import SoundController.Sound._
  import SoundController._
  import SoundControllerImpl._

  private implicit val ev: EventContext = EventContext.Implicits.global
  private implicit val ec: DispatchQueue = Threading.Background

  private val accountsService = inject[AccountsService]

  private def registerCustomUri(userId: UserId, mm: MediaManager, uri: Option[Uri], sounds: Seq[Sound]): Unit = {
    verbose(s"Register custom sound uri: $uri. For sounds: ${sounds.map(_.getClass.getSimpleName).mkString(", ")}. For user: $userId. For media manager: $mm")
    sounds.map(createSoundMediaManagerKey).foreach { sKey =>
      uri.fold(mm.unregisterMedia(sKey))(mm.registerMediaFileUrl(sKey, _))
    }
  }

  private def mediaManagerWithSettings(zms: ZMessaging): Signal[(UserId, SoundSettings)] = {
    val soundIntensityPref = zms.userPrefs.preference(UserPreferences.Sounds).signal.optional
    val ringtonePref = zms.userPrefs.preference(UserPreferences.RingTone).signal.optional
    val texttonePref = zms.userPrefs.preference(UserPreferences.TextTone).signal.optional
    val pingtonePref = zms.userPrefs.preference(UserPreferences.PingTone).signal.optional

    (soundIntensityPref zip ringtonePref zip texttonePref zip pingtonePref)
      .map { case (((soundIntensity, ringTone), textTone), pingTone) =>
      val settings = SoundSettings(
        soundIntensity,
        ringTone.filter(_.nonEmpty).map(Uri.parse),
        textTone.filter(_.nonEmpty).map(Uri.parse),
        pingTone.filter(_.nonEmpty).map(Uri.parse)
      )
      verbose(s"New settings for user: ${zms.selfUserId}. $settings")
      zms.selfUserId -> settings
    }
  }

  private val userIdsWithSettings =
    for {
      zmss <- accountsService.zmsInstances
      userIdsWithSettings <- Signal.traverse(zmss.toList)(mediaManagerWithSettings)
    } yield userIdsWithSettings

  userIdsWithSettings {
    _ foreach { case (userId, settings) =>
      withMediaManager(userId) { mm =>
        registerCustomUri(userId, mm, settings.ringTone, Seq(IncomingCallRingtone, OutgoingCallRingtone, IncomingCallRingtoneInCall))
        registerCustomUri(userId, mm, settings.pingTone, Seq(PingFromMe, PingFromThem))
        registerCustomUri(userId, mm, settings.textTone, Seq(IncomingMessage, FirstIncomingMessage))
      }
    }
  }

  private val mediaManagersByUserId = for {
    zmss <- accountsService.zmsInstances
    userIdsWithZms <- Signal.traverse(zmss.toList)(zms => Signal.future(zms.mediamanager.mediaManager.map(zms.selfUserId -> _)))
  } yield userIdsWithZms.toMap

  private def withMediaManager(userId: UserId)(action: MediaManager => Unit): Unit = {
    mediaManagersByUserId.head.map(_.get(userId)) foreach {
      case Some(mm) => action(mm)
      case None =>
        //TODO We should do something with this case
        verbose(s"Can not find media manager for the user: $userId")
    }
  }

  override def play(userId: UserId, sound: Sound): Unit = {
    verbose(s"Play sound: ${sound.getClass.getSimpleName}. Sound key: ${createSoundMediaManagerKey(sound)}. UserId: $userId.")
    withMediaManager(userId)(_.playMedia(createSoundMediaManagerKey(sound)))
  }

  override def stop(userId: UserId, sound: Sound): Unit = {
    verbose(s"Stop sound: ${sound.getClass.getSimpleName}. Sound key: ${createSoundMediaManagerKey(sound)}. UserId: $userId.")
    withMediaManager(userId)(_.stopMedia(createSoundMediaManagerKey(sound)))
  }

  override def getCurrentSettings(userId: UserId): Option[SoundSettings] = {
    userIdsWithSettings.currentValue.flatMap(_.toMap.get(userId))
  }
}

object SoundControllerImpl {
  import SoundController.Sound
  import SoundController.Sound._

  def createSoundMediaManagerKey(sound: Sound)(implicit ctx: Context): String =
    getResEntryName(getSoundDefaultRawId(sound))

  def getSoundDefaultRawId(sound: Sound): Int = sound match {
    case IncomingCallRingtone => R.raw.ringing_from_them
    case IncomingCallRingtoneInCall => R.raw.ringing_from_them_incall
    case OutgoingCallRingtone => R.raw.ringing_from_me
    case OutgoingVideoCallRingtone => R.raw.ringing_from_me_video
    case PingFromMe => R.raw.ping_from_me
    case PingFromThem => R.raw.ping_from_them
    case FirstIncomingMessage => R.raw.first_message
    case IncomingMessage => R.raw.new_message
    case Alert => R.raw.alert
    case CameraShutter => R.raw.camera
    case CallEnded => R.raw.talk_later
    case CallDropped => R.raw.call_drop
    case CallEstablished => R.raw.ready_to_talk
  }

}

trait VibrationController {
  def isVibrationEnabled(userId: UserId): Boolean
  def incomingRingToneVibration(userId: UserId): Unit
  def callEstablishedVibration(userId: UserId): Unit
  def callEndedVibration(userId: UserId): Unit
  def callDroppedVibration(userId: UserId): Unit
  def alertVibration(userId: UserId): Unit
  def shortVibration(userId: UserId): Unit
  def messageIncomingVibration(userId: UserId): Unit
  def pingFromMeVibration(userId: UserId): Unit
  def pingFromThemVibration(userId: UserId): Unit
  def cameraShutterVibration(userId: UserId): Unit
  def cancelCurrentVibration(): Unit
}

class VibrationControllerImpl(implicit inj: Injector, cxt: Context) extends VibrationController with Injectable {

  private val vibrator = inject[Vibrator]
  private val accountsService = inject[AccountsService]

  private def userIdWithVibrationEnabled(zms: ZMessaging): Signal[(UserId, Boolean)] =
    zms.userPrefs.preference(UserPreferences.VibrateEnabled).signal.map(zms.selfUserId -> _)

  private val isVibrationEnabled =
    for {
      zmss <- accountsService.zmsInstances
      userIdsWithVibrationEnabled <- Signal.traverse(zmss.toList)(userIdWithVibrationEnabled)
    } yield userIdsWithVibrationEnabled.toMap

  private def vibrate(userId: UserId, patternId: Int, loop: Boolean = false): Unit = {
    cancelCurrentVibration()
    if (isVibrationEnabled(userId)) {
      DeprecationUtils.vibrate(vibrator, getIntArray(patternId).map(_.toLong), if (loop) 0 else -1)
    }
  }

  override def isVibrationEnabled(userId: UserId): Boolean =
    isVibrationEnabled.currentValue.flatMap(_.get(userId)).getOrElse(true)

  override def incomingRingToneVibration(userId: UserId): Unit =
    vibrate(userId, R.array.ringing_from_them, loop = true)

  override def callEstablishedVibration(userId: UserId): Unit =
    vibrate(userId, R.array.ready_to_talk)

  override def callEndedVibration(userId: UserId): Unit =
    vibrate(userId, R.array.talk_later)

  override def callDroppedVibration(userId: UserId): Unit =
    vibrate(userId, R.array.call_dropped)

  override def alertVibration(userId: UserId): Unit =
    vibrate(userId, R.array.alert)

  override def shortVibration(userId: UserId): Unit =
    vibrate(userId, R.array.alert)

  override def messageIncomingVibration(userId: UserId): Unit =
    vibrate(userId, R.array.new_message)

  override def pingFromThemVibration(userId: UserId): Unit =
    vibrate(userId, R.array.ping_from_them)

  override def pingFromMeVibration(userId: UserId): Unit =
    vibrate(userId, R.array.ping_from_them)

  override def cameraShutterVibration(userId: UserId): Unit =
    vibrate(userId, R.array.camera)

  override def cancelCurrentVibration(): Unit =
    vibrator.cancel()

}
