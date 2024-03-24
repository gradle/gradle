package org.gradle.client.logic

import java.io.File
import java.nio.charset.Charset
import java.security.MessageDigest

object Constants {

    const val APPLICATION_NAME = "gradle-client"

    const val APPLICATION_DISPLAY_NAME = "Gradle Client"

    val INSTALLATION_IDENTIFIER = calculateInstallationIdentifier()
}

private val ownExecutable = File(ProcessHandle.current().info().command().get())

private class JarProbe

@OptIn(ExperimentalStdlibApi::class)
private fun calculateInstallationIdentifier(): String {
    val ownJar = File(JarProbe::class.java.protectionDomain.codeSource.location.toURI())
    val installIdentifierDir =
        if (ownExecutable.nameWithoutExtension == "java") ownJar.parentFile
        else ownExecutable.parentFile
    val hashBytes = MessageDigest.getInstance("MD5").digest(
        installIdentifierDir.absolutePath.toByteArray(Charset.defaultCharset())
    )
    // TODO consider using a shorter hash encoding
    return hashBytes.toHexString()
}
