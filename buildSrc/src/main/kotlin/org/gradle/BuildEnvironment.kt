package org.gradle

object BuildEnvironment {
    val isCiServer = "CI" in System.getenv()
}
