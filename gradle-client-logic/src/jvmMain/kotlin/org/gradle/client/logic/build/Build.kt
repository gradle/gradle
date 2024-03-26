package org.gradle.client.logic.build

import org.gradle.client.logic.util.generateIdentity
import java.io.File

data class Build(
    val id: String = generateIdentity(),
    val rootDir: File,
)
