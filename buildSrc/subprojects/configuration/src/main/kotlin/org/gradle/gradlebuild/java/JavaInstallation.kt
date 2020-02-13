package org.gradle.gradlebuild.java

import org.gradle.api.JavaVersion
import org.gradle.api.file.FileCollection
import java.io.File


class JavaInstallation(
    val current: Boolean,
    val javaInstallation: org.gradle.jvm.toolchain.JavaInstallation
) {

    override fun toString(): String = "$vendorAndMajorVersion (${javaHome.absolutePath})"

    val javaHome: File
        get() = javaInstallation.installationDirectory.asFile

    val javaVersion: JavaVersion
        get() = javaInstallation.javaVersion

    val javaExecutable: File
        get() = javaInstallation.javaExecutable.asFile

    val toolsClasspath: FileCollection
        get() = javaInstallation.jdk.get().toolsClasspath

    val vendorAndMajorVersion: String
    get() = "${javaInstallation.implementationName} ${javaInstallation.javaVersion}"
}
