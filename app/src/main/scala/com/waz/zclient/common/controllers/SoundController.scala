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
import com.waz.ZLog.{LogTag, verbose}
import com.waz.content.UserPreferences
import com.waz.media.manager.MediaManager
import com.waz.model.UserId
import com.waz.service.{AccountsService, MediaManagerService, ZMessaging}
import com.waz.threading.{DispatchQueue, Threading}
import com.waz.utils.events.{EventContext, Signal}
import com.waz.zclient.common.controllers.SoundController2.Sound
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.DeprecationUtils
import com.waz.zclient.{R, _}

import scala.collection.generic.CanBuildFrom
import scala.concurrent.Await
import scala.concurrent.duration.{FiniteDuration, _}
import scala.util.Try


//trait SoundController {
//  def currentTonePrefs: (String, String, String)
//
//  def isVibrationEnabled(userId: UserId): Boolean
//  def isVibrationEnabledInCurrentZms: Boolean
//  def soundIntensityNone: Boolean
//  def soundIntensityFull: Boolean
//
//  def setIncomingRingTonePlaying(userId: UserId, play: Boolean): Unit
//  def setOutgoingRingTonePlaying(play: Boolean, isVideo: Boolean = false): Unit
//
//  def playCallEstablishedSound(userId: UserId): Unit
//  def playCallEndedSound(userId: UserId): Unit
//  def playCallDroppedSound(): Unit
//  def playAlert(): Unit
//  def shortVibrate(): Unit
//  def playMessageIncomingSound(firstMessage: Boolean): Unit
//  def playPingFromThem(): Unit
//  def playPingFromMe(): Unit
//  def playCameraShutterSound(): Unit
//  def playRingFromThemInCall(play: Boolean): Unit
//}


////TODO Dean - would be nice to change these unit methods to listeners on signals from the classes that could trigger sounds.
////For that, however, we would need more signals in the app, and hence more scala classes...
//class SoundControllerImpl(implicit inj: Injector, cxt: Context) extends SoundController with Injectable {
//
//  private implicit val ev = EventContext.Implicits.global
//  private implicit val ec = Threading.Background
//
//  private val zms = inject[Signal[ZMessaging]]
//  private val audioManager = Option(inject[AudioManager])
//  private val vibrator = Option(inject[Vibrator])
//  private val accountsService = inject[AccountsService]
//
//  private val mediaManager = zms.flatMap(z => Signal.future(z.mediamanager.mediaManager))
//  private val soundIntensity = zms.flatMap(_.mediamanager.soundIntensity)
//
//  private var _mediaManager = Option.empty[MediaManager]
//  mediaManager(m => _mediaManager = Some(m))
//
//  //TODO Refactor MessageNotificationsController and remove this. Work with normal Signal.head method instead
//  private implicit class RichSignal[T](val value: Signal[T]) {
//    def headSync(timeout: FiniteDuration = 3.seconds)(implicit logTag: LogTag): Option[T] =
//      Try(Await.result(value.head(logTag), timeout)).toOption
//  }
//
//  def currentTonePrefs: (String, String, String) = tonePrefs.currentValue.getOrElse((null, null, null))
//
//  private val tonePrefs = (for {
//    zms <- zms
//    ringTone <- zms.userPrefs.preference(UserPreferences.RingTone).signal
//    textTone <- zms.userPrefs.preference(UserPreferences.TextTone).signal
//    pingTone <- zms.userPrefs.preference(UserPreferences.PingTone).signal
//  } yield (ringTone, textTone, pingTone)).disableAutowiring()
//
//  tonePrefs {
//    case (ring, text, ping) => setCustomSoundUrisFromPreferences(ring, text, ping)
//  }
//
//  private val currentZmsVibrationEnabled =
//    zms.flatMap(_.userPrefs.preference(UserPreferences.VibrateEnabled).signal).disableAutowiring()
//
//  override def isVibrationEnabledInCurrentZms: Boolean =
//    currentZmsVibrationEnabled.headSync().getOrElse(false)
//
//  override def isVibrationEnabled(userId: UserId): Boolean = {
//    (for {
//      zms <- Signal.future(accountsService.getZms(userId)).collect { case Some(v) => v }
//      isEnabled <- zms.userPrefs.preference(UserPreferences.VibrateEnabled).signal
//    } yield isEnabled).headSync().getOrElse(false)
//  }
//
//  override def soundIntensityNone: Boolean =
//    soundIntensity.currentValue.contains(IntensityLevel.NONE)
//  override def soundIntensityFull: Boolean =
//    soundIntensity.currentValue.isEmpty || soundIntensity.currentValue.contains(IntensityLevel.FULL)
//
//  override def setIncomingRingTonePlaying(userId: UserId, play: Boolean): Unit = {
//    if (!soundIntensityNone) setMediaPlaying(R.raw.ringing_from_them, play)
//    setVibrating(R.array.ringing_from_them, play, loop = true, Some(userId))
//  }
//
//  //no vibration needed here
//  //TODO - there seems to be a race condition somewhere, where this method is called while isVideo is incorrect
//  //This leads to the case where one of the media files starts playing, and we never receive the stop for it. Always ensuring
//  //that both files stops is a fix for the symptom, but not the root cause - which could be affecting other things...
//  override def setOutgoingRingTonePlaying(play: Boolean, isVideo: Boolean = false): Unit =
//    if (play) {
//      if (soundIntensityFull) setMediaPlaying(if (isVideo) R.raw.ringing_from_me_video else R.raw.ringing_from_me, play = true)
//    } else {
//      setMediaPlaying(R.raw.ringing_from_me_video, play = false)
//      setMediaPlaying(R.raw.ringing_from_me, play = false)
//    }
//
//  override def playCallEstablishedSound(userId: UserId): Unit = {
//    if (soundIntensityFull) setMediaPlaying(R.raw.ready_to_talk)
//    setVibrating(R.array.ready_to_talk, userId = Some(userId))
//  }
//
//  override def playCallEndedSound(userId: UserId): Unit = {
//    if (soundIntensityFull) setMediaPlaying(R.raw.talk_later)
//    setVibrating(R.array.talk_later, userId = Some(userId))
//  }
//
//  override def playCallDroppedSound(): Unit = {
//    if (soundIntensityFull) setMediaPlaying(R.raw.call_drop)
//    setVibrating(R.array.call_dropped)
//  }
//
//  override def playAlert(): Unit = {
//    if (soundIntensityFull) setMediaPlaying(R.raw.alert)
//    setVibrating(R.array.alert)
//  }
//
//  def shortVibrate(): Unit =
//    setVibrating(R.array.alert)
//
//  def playMessageIncomingSound(firstMessage: Boolean): Unit = {
//    if (firstMessage && !soundIntensityNone) setMediaPlaying(R.raw.first_message)
//    else if (soundIntensityFull) setMediaPlaying(R.raw.new_message)
//    setVibrating(R.array.new_message)
//  }
//
//  def playPingFromThem(): Unit = {
//    if (!soundIntensityNone) setMediaPlaying(R.raw.ping_from_them)
//    setVibrating(R.array.ping_from_them)
//  }
//
//  //no vibration needed
//  def playPingFromMe(): Unit =
//    if (!soundIntensityNone) setMediaPlaying(R.raw.ping_from_me)
//
//  def playCameraShutterSound(): Unit = {
//    if (soundIntensityFull) setMediaPlaying(R.raw.camera)
//    setVibrating(R.array.camera)
//  }
//
//  def playRingFromThemInCall(play: Boolean): Unit =
//    setMediaPlaying(R.raw.ringing_from_them_incall, play)
//
//  /**
//    * @param play For looping patterns, this parameter will tell to stop vibrating if they have previously been started
//    */
//  private def setVibrating(patternId: Int, play: Boolean = true, loop: Boolean = false, userId: Option[UserId] = None): Unit = {
//    (audioManager, vibrator) match {
//      case (Some(am), Some(vib)) if play &&
//                                    am.getRingerMode != AudioManager.RINGER_MODE_SILENT &&
//                                    userId.fold(isVibrationEnabledInCurrentZms)(isVibrationEnabled) =>
//        vib.cancel() // cancel any current vibrations
//        DeprecationUtils.vibrate(vib, getIntArray(patternId).map(_.toLong), if (loop) 0 else -1)
//      case (_, Some(vib)) => vib.cancel()
//      case _ =>
//    }
//  }
//
//  /**
//    * @param play For media that play for a long time (or continuously??) this parameter will stop them
//    */
//  private def setMediaPlaying(resourceId: Int, play: Boolean = true) = _mediaManager.foreach { mm =>
//    val resName = getResEntryName(resourceId)
//    verbose(s"setMediaPlaying: $resName, play: $play")
//    if (play) mm.playMedia(resName) else mm.stopMedia(resName)
//  }
//
//  /**
//    * Takes a saved "URL" from the apps shared preferences, and uses that to set the different sounds in the app.
//    * There are several "groups" of sounds, each with their own uri. There is then also a given "mainId" for each group,
//    * which gets set first, and is then used to determine if the uri points to the "default" sound file.
//    *
//    * Then for the other ids related to that group, they are all set to either the default, or whatever new uri is specified
//    */
//  def setCustomSoundUrisFromPreferences(ringTonePref: String, textTonePref: String, pingTonePref: String): Unit = {
//    setCustomSoundUrisFromPreferences(ringTonePref, R.raw.ringing_from_them, Seq(R.raw.ringing_from_me, R.raw.ringing_from_me_video, R.raw.ringing_from_them_incall))
//    setCustomSoundUrisFromPreferences(pingTonePref, R.raw.ping_from_them,    Seq(R.raw.ping_from_me))
//    setCustomSoundUrisFromPreferences(textTonePref, R.raw.new_message,       Seq(R.raw.first_message, R.raw.new_message_gcm))
//  }
//
//  private def setCustomSoundUrisFromPreferences(uri: String, mainId: Int, otherIds: Seq[Int]): Unit = {
//    val isDefault = TextUtils.isEmpty(uri) || isDefaultValue(cxt, uri, R.raw.ringing_from_them)
//    val finalUri  = if (isDefault) getUriForRawId(cxt, R.raw.ringing_from_them).toString
//                    else if(RingtoneUtils.isSilent(uri)) ""
//                    else uri
//
//    setCustomSoundUri(mainId, finalUri)
//    otherIds.foreach(id => setCustomSoundUri(id, if (isDefault) getUriForRawId(cxt, id).toString else finalUri))
//  }
//
//  private def setCustomSoundUri(resourceId: Int, uri: String) = {
//    try {
//      _mediaManager.foreach { mm =>
//        if (TextUtils.isEmpty(uri)) mm.unregisterMedia(getResEntryName(resourceId))
//        else mm.registerMediaFileUrl(getResEntryName(resourceId), Uri.parse(uri))
//      }
//    }
//    catch {
//      case e: Exception => error(s"Could not set custom uri: $uri", e)
//    }
//  }
//}

trait SoundController2 {
  def play(userId: UserId, sound: Sound): Unit
  def stop(userId: UserId, sound: Sound): Unit
}

object SoundController2 {

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

  case class SoundSettings(ringTone: Option[Uri], textTone: Option[Uri], pingTone: Option[Uri])
}

class SoundConroller2Impl(implicit inj: Injector, cxt: Context) extends SoundController2 with Injectable {
  import SignalUtils._
  import SoundConroller2Impl._
  import SoundController2.Sound._
  import SoundController2._

  private implicit val ev: EventContext = EventContext.Implicits.global
  private implicit val ec: DispatchQueue = Threading.Background

  private val accountsService = inject[AccountsService]

  private def registerCustomUri(mm: MediaManager, uri: Option[Uri], sounds: Seq[Sound]): Unit = {
    sounds.map(createSoundMediaManagerKey).foreach { sKey =>
      uri.fold(mm.unregisterMedia(sKey))(mm.registerMediaFileUrl(sKey, _))
    }
  }

  private def mediaManagerWithSettings(zms: ZMessaging): Signal[(MediaManagerService, SoundSettings)] =
    for {
      ringTone <- zms.userPrefs.preference(UserPreferences.RingTone).signal.optional
      textTone <- zms.userPrefs.preference(UserPreferences.TextTone).signal.optional
      pingTone <- zms.userPrefs.preference(UserPreferences.PingTone).signal.optional
    } yield
      zms.mediamanager ->
      SoundSettings(ringTone.map(Uri.parse), textTone.map(Uri.parse), pingTone.map(Uri.parse))

  (for {
    zmss <- accountsService.zmsInstances
    managersWithSettings <- SignalUtils.traverse(zmss)(mediaManagerWithSettings)
  } yield managersWithSettings) { managersWithSettings =>
    managersWithSettings foreach { case (manager, settings) =>
      manager.mediaManager.foreach { mm =>
        registerCustomUri(mm, settings.ringTone, Seq(IncomingCallRingtone, OutgoingCallRingtone, IncomingCallRingtoneInCall))
        registerCustomUri(mm, settings.pingTone, Seq(PingFromMe, PingFromThem))
        registerCustomUri(mm, settings.textTone, Seq(IncomingMessage, FirstIncomingMessage))
      }
    }
  }

  private val mediaManagersByUserId =
    for {
      zmss <- accountsService.zmsInstances
      userIdsWithZms <- SignalUtils.traverse(zmss)(zms => Signal.future(zms.mediamanager.mediaManager.map(zms.selfUserId -> _)))
    } yield userIdsWithZms.toMap

  private def withMediaManager(userId: UserId)(action: MediaManager => Unit): Unit = {
    mediaManagersByUserId.currentValue.flatMap(_.get(userId)) match {
      case Some(mm) => action(mm)
      case None =>
        //TODO We should do something with this case
        verbose(s"Can not find media manager for the user: $userId")
    }
  }

  def play(userId: UserId, sound: Sound): Unit =
    withMediaManager(userId)(_.playMedia(createSoundMediaManagerKey(sound)))

  def stop(userId: UserId, sound: Sound): Unit =
    withMediaManager(userId)(_.stopMedia(createSoundMediaManagerKey(sound)))

}

object SoundConroller2Impl {
  import SoundController2.Sound
  import SoundController2.Sound._

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
      userIdsWithVibrationEnabled <- SignalUtils.traverse(zmss)(userIdWithVibrationEnabled)
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

//TODO move this methods into Signal
object SignalUtils {
  import scala.language.higherKinds

  implicit class SignalOpt[T](val value: Signal[T]) {
    def optional: Signal[Option[T]] = value.map(Option(_)).orElse(Signal.const(None))
    def headSync(timeout: FiniteDuration = 3.seconds)(implicit logTag: LogTag): Option[T] =
      Try(Await.result(value.head(logTag), timeout)).toOption
  }

  def traverse[A, B, M[X] <: TraversableOnce[X]](in: M[A])
                                                (fn: A => Signal[B])
                                                (implicit cbf: CanBuildFrom[M[A], B, M[B]]): Signal[M[B]] =
    in.foldLeft(Signal.const(cbf(in))) { (fr, a) =>
      val fb = fn(a)
      for (r <- fr; b <- fb) yield r += b
    }.map(_.result())
}
