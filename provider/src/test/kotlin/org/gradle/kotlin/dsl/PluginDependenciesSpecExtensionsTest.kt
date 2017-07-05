package org.gradle.kotlin.dsl

import org.junit.Test

class PluginDependenciesSpecExtensionsTest {

    @Test
    fun `given build-scan plugin accessor, it should create a single request with default version`() {
        expecting(plugin(id = "com.gradle.build-scan", version = "1.8")) {
            `build-scan`
        }
    }

    @Test
    fun `given build-scan plugin accessor with version, it should create a single request with given version`() {
        expecting(plugin(id = "com.gradle.build-scan", version = "1.7.1")) {
            `build-scan` version "1.7.1"
        }
    }
}
