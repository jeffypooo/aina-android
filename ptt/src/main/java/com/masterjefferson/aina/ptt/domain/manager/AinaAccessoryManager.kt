package com.masterjefferson.aina.ptt.domain.manager

import com.masterjefferson.aina.ptt.domain.model.AinaButtonEvent
import io.reactivex.Observable

interface AinaAccessoryManager {
  val connected: Boolean
  val connectedObservable: Observable<Boolean>
  val buttonEventsObservable: Observable<AinaButtonEvent>

  fun dispose()
}