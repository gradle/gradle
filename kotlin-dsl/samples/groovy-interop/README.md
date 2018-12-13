groovy-interop
==============

This sample demonstrates how to interact with Groovy code from Kotlin.

The [Kotlin build script](./build.gradle.kts) applies [a Groovy build script](./groovy.gradle) which then exposes a `Closure` as an extra project property.

The exposed `Closure` is imported in Kotlin as a [delegated property](./build.gradle.kts#L5) and invoked by the registered Kotlin tasks.

