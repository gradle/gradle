package org.gradle.gradlebuild

object BuildEnvironment {
    val isCiServer = "CI" in System.getenv()
}
