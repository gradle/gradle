package org.gradle.client.core.util

import java.util.*

fun generateIdentity(): String =
    UUID.randomUUID().toString()
