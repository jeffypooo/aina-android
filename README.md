# aina-android

Use Aina's [PTT Voice Responder](https://www.aina-wireless.com/shop/aina-ptt-voice-responder/) in your Android app.

# User Guide

### Installation

Add the library to your `build.gradle`:

```gradle
repositories {
    jcenter()
}

dependencies {
  implementation 'com.masterjefferson:aina-ptt:0.0.2'
}
```

### Permissions

Bluetooth permission is required:

```xml
<uses-permission android:name="android.permission.BLUETOOTH" />
```

### Monitoring the connection state

```kotlin
import io.reactivex.Observable
import io.reactivex.disposables.Disposable
import com.masterjefferson.aina.ptt.android.AndroidAinaManager
import com.masterjefferson.aina.ptt.domain.model.AinaButton


private val ainaManager by lazy { AndroidAinaManager(this) }

fun monitorAinaConnection() {
  val disposable = ainaManager.connectedObservable
        .subscribe(
            { connected -> Log.i(TAG, "voice responder ${if (connected) "connected" else "disconnected"}") },
            { err -> Log.e(TAG, "error thrown by aina connection observable: $err") }
        )
}
```

You can also use `AinaAccessoryManager.connected` to quickly check if the accessory is connected.

### Consuming button presses

```kotlin
import io.reactivex.Observable
import io.reactivex.disposables.Disposable
import com.masterjefferson.aina.ptt.android.AndroidAinaManager
import com.masterjefferson.aina.ptt.domain.model.AinaButton



private val ainaManager by lazy { AndroidAinaManager(this) }

fun consumeAinaButtonEvents() {
  val disposable = ainaManager.buttonEventsObservable
        .subscribe(
            { event -> Log.i(TAG, "${event.which} is ${if (event.pressed) "PRESSED" else "RELEASED"}") },
            { err -> Log.e(TAG, "error thrown by aina buttons: $err") }
        )
}
```
