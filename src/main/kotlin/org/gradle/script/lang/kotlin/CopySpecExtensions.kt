package org.gradle.script.lang.kotlin

import org.gradle.api.file.CopySpec

// Interim extensions until those methods are added to CopySpec
fun CopySpec.from(path: Any, setup: (CopySpec) -> Unit): CopySpec =
    from(path).apply { setup(this) }

fun CopySpec.into(path: Any, setup: (CopySpec) -> Unit): CopySpec =
    into(path).apply { setup(this) }
