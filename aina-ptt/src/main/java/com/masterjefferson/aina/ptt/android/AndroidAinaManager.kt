package com.masterjefferson.aina.ptt.android

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.masterjefferson.aina.ptt.android.bluetooth.connectSppSocket
import com.masterjefferson.aina.ptt.android.bluetooth.isAinaPttVoiceResponder
import com.masterjefferson.aina.ptt.domain.manager.AinaAccessoryManager
import com.masterjefferson.aina.ptt.domain.model.AinaButton
import com.masterjefferson.aina.ptt.domain.model.AinaButtonEvent
import io.reactivex.Observable
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.PublishSubject
import java.io.InputStream
import java.lang.ref.WeakReference
import java.util.*

class AndroidAinaManager(context: Context) : BluetoothProfile.ServiceListener,
    AinaAccessoryManager {

  override val connected: Boolean
    get() = connectedSubject.value ?: false
  override val connectedObservable: Observable<Boolean>
    get() = connectedSubject
  override val buttonEventsObservable: Observable<AinaButtonEvent>
    get() = buttonEventSubject

  private val contextRef = WeakReference(context)
  private val connectedSubject = BehaviorSubject.create<Boolean>()
  private val buttonEventSubject = PublishSubject.create<AinaButtonEvent>()
  private var headsetProfile: BluetoothHeadset? = null
  private var ainaDevice: BluetoothDevice? = null
  private var ioThread: Thread? = null
  private var sppSocket: BluetoothSocket? = null

  private val bluetoothAdapter: BluetoothAdapter?
    get() = BluetoothAdapter.getDefaultAdapter()

  private val connectionEventReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
      val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
        ?: return
      val connected = intent.action == BluetoothDevice.ACTION_ACL_CONNECTED
      if (device.isAinaPttVoiceResponder) {
        ainaDevice = if (connected) device else null
        updateConnectedState()
      }
    }
  }

  init {
    bluetoothAdapter?.getProfileProxy(context, this, BluetoothProfile.HEADSET)
    context.apply {
      val filter = IntentFilter().apply {
        addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
        addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
      }
      registerReceiver(connectionEventReceiver, filter)
    }
  }

  override fun dispose() {
    stopSppIo()
    contextRef.apply {
      get()?.unregisterReceiver(connectionEventReceiver)
      clear()
    }
    headsetProfile?.apply {
      Log.v(TAG, "closing profile proxy")
      bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HEADSET, this)
    }
  }

  override fun onServiceDisconnected(profile: Int) {
    if (BluetoothHeadset.HEADSET == profile) {
      Log.v(TAG, "headset service disconnected")
      headsetProfile = null
    }
  }

  @SuppressLint("MissingPermission")
  override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
    if (BluetoothHeadset.HEADSET == profile) {
      Log.v(TAG, "headset service connected")
      headsetProfile = proxy as BluetoothHeadset
      ainaDevice = headsetProfile!!.connectedDevices.find { it.isAinaPttVoiceResponder }
      updateConnectedState()
    }
  }

  private fun updateConnectedState() {
    val connected = ainaDevice != null
    Log.v(TAG, "voice responder connected: $connected")
    connectedSubject.onNext(connected)
    if (connected) {
      startSppIo()
    } else {
      stopSppIo()
    }
  }

  private fun startSppIo() {
    Log.v(TAG, "starting io thread...")
    ioThread = Thread({ ioLoop() }, "aina-spp-io").apply { start() }
  }

  private fun stopSppIo() {
    try {
      sppSocket?.apply {
        Log.v(TAG, "closing spp socket...")
        close()
      }
      sppSocket = null
    } catch (err: Throwable) {
      Log.v(TAG, "exception thrown closing SPP socket: $err")
    }
  }

  private fun ioLoop() {
    Log.v(TAG, "io thread started")
    val device = ainaDevice ?: return
    Log.v(TAG, "connecting SPP socket...")
    sppSocket = try {
      device.connectSppSocket()
    } catch (e: Throwable) {
      Log.w(TAG, "failed to connect SPP socket")
      return
    }
    Log.v(TAG, "connected.")
    val readBuffer = ByteArray(64)
    while (true) {
      val data = try {
        sppSocket!!.inputStream.readCopying(readBuffer)
      } catch (e: Throwable) {
        Log.w(TAG, "exception reading from spp socket", e)
        sppSocket?.close()
        sppSocket = null
        break
      }
      val str = String(data)
      val buttonEvent = str.asAinaButtonEvent()
      if (null == buttonEvent) {
        Log.w(TAG, "unknown button event: $str")
      } else {
        Log.v(TAG, "EVENT: $buttonEvent")
        buttonEventSubject.onNext(buttonEvent)
      }
    }
    Log.v(TAG, "io thread terminating")
  }

  companion object {
    private const val TAG = "AndroidAinaManager"
  }
}

private fun String.asAinaButtonEvent(): AinaButtonEvent? {
  return when {
    contains("+PTT=")   -> AinaButtonEvent(AinaButton.PTT1, last() == 'P')
    contains("+PTTS=")  -> AinaButtonEvent(AinaButton.PTT2, last() == 'P')
    contains("+PTTE=")  -> AinaButtonEvent(AinaButton.EMERGENCY, last() == 'P')
    contains("+PTTB1=") -> AinaButtonEvent(AinaButton.LEFT, last() == 'P')
    contains("+PTTB2=") -> AinaButtonEvent(AinaButton.RIGHT, last() == 'P')
    contains("+VGS=U")  -> AinaButtonEvent(AinaButton.VOL_UP, false)
    contains("+VGS=D")  -> AinaButtonEvent(AinaButton.VOL_DOWN, false)
    else                -> null
  }
}

private fun InputStream.readCopying(buffer: ByteArray): ByteArray {
  return read(buffer).let { Arrays.copyOfRange(buffer, 0, it) }
}