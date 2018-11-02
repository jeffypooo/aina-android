package com.masterjefferson.aina.ptt.android.bluetooth



import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import java.util.*

private val UUID_GENERIC_SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

internal val BluetoothDevice.isAinaPttVoiceResponder: Boolean
  @SuppressLint("MissingPermission")
  get() = name.contains("APTT")

@SuppressLint("MissingPermission")
internal fun BluetoothDevice.connectSppSocket(retryCount: Int = 3): BluetoothSocket {
  var attempts = 0
  while (true) {
    try {
      return createRfcommSocketToServiceRecord(UUID_GENERIC_SPP).apply { connect() }
    } catch (e: Throwable) {
      if (++attempts == retryCount) {
        throw e
      }
    }
  }
}