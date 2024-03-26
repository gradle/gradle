package org.gradle.client.logic.util

import java.util.UUID

fun generateIdentity(): String =
    UUID.randomUUID().toString()
