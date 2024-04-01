package org.gradle.client.core.util

import java.util.UUID

fun generateIdentity(): String =
    UUID.randomUUID().toString()
