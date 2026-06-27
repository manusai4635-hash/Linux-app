package com.linuxify.utils

import java.net.ServerSocket

object NetworkHelper { fun freePort(): Int = ServerSocket(0).use { it.localPort } }
