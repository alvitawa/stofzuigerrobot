package com.macroyau.blue2serial.ext

inline fun <reified T : Any> T.logID() = T::class.java.simpleName