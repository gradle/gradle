package org.gradle.script.lang.kotlin

import org.gradle.api.file.CopySpec
import org.gradle.api.internal.file.copy.CopySpecInternal

// Interim extensions until those methods are added to CopySpec
fun CopySpec.from(path: Any, setup: (CopySpec) -> Unit): CopySpec =
    addChild {
        from(path)
        setup(this)
    }

fun CopySpec.into(path: Any, setup: (CopySpec) -> Unit): CopySpec =
    addChild {
        into(path)
        setup(this)
    }

private inline fun CopySpec.addChild(crossinline setup: CopySpec.() -> Unit): CopySpec =
    (this as CopySpecInternal).addChild().apply(setup)
