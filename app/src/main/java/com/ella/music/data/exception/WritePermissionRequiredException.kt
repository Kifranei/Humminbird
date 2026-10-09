package com.ella.music.data.exception

import android.content.IntentSender

class WritePermissionRequiredException(val intentSender: IntentSender) : Exception("Write permission required")
