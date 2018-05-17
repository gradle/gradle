package org.gradle.kotlin.dsl.experiments.plugins

import java.util.Properties


internal
object DefaultVersions {

    val ktlint: String by DEFAULT_VERSIONS
}


private
val DEFAULT_VERSIONS =
    Properties().also { props ->
        DefaultVersions::javaClass.get().getResourceAsStream("default-versions.properties")!!.use { input ->
            props.load(input)
        }
    }
