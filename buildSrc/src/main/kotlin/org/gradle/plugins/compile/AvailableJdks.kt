package org.gradle.plugins.compile

import org.gradle.api.JavaVersion
import org.gradle.internal.jvm.Jvm
import org.gradle.jvm.toolchain.internal.JavaInstallationProbe
import org.gradle.jvm.toolchain.internal.LocalJavaInstallation
import java.io.File

class DefaultJavaInstallation(val current: Boolean) : LocalJavaInstallation {
    private lateinit var name: String
    private lateinit var javaVersion: JavaVersion
    private lateinit var javaHome: File
    private lateinit var displayName: String

    override fun getName() = name
    override fun getDisplayName() = displayName
    override fun setDisplayName(displayName: String) { this.displayName = displayName }
    override fun getJavaVersion() = javaVersion
    override fun setJavaVersion(javaVersion: JavaVersion) { this.javaVersion = javaVersion }
    override fun getJavaHome() = javaHome
    override fun setJavaHome(javaHome: File) { this.javaHome = javaHome }
    fun getToolsJar(): File? = Jvm.forHome(javaHome).toolsJar

    override fun toString(): String = "${displayName} (${javaHome.absolutePath})"
}

fun findJavaInstallations(javaHomes: List<String>, javaInstallationProbe: JavaInstallationProbe): Map<JavaVersion, DefaultJavaInstallation> =
    javaHomes.map { javaHome ->
        val javaInstallation = DefaultJavaInstallation(false)
        javaInstallation.javaHome = File(javaHome)
        javaInstallationProbe.checkJdk(File(javaHome)).configure(javaInstallation)
        javaInstallation
    }.associateBy { it.javaVersion }

open class AvailableJdks(javaHomes: List<String>, javaInstallationProbe: JavaInstallationProbe) {
    private val javaInstallations: Map<JavaVersion, DefaultJavaInstallation> = findJavaInstallations(javaHomes, javaInstallationProbe)
    private val currentJavaInstallation: DefaultJavaInstallation
    init {
        val current = DefaultJavaInstallation(true)
        javaInstallationProbe.current(current)
        current.javaHome = Jvm.current().javaHome
        currentJavaInstallation = current
    }

    fun jdkFor(javaVersion: JavaVersion): DefaultJavaInstallation = when {
        javaVersion == currentJavaInstallation.javaVersion -> currentJavaInstallation
        javaInstallations.containsKey(javaVersion) -> javaInstallations[javaVersion]!!
        javaVersion <= currentJavaInstallation.javaVersion -> currentJavaInstallation
        else -> throw IllegalArgumentException("No Java installation found which supports Java version $javaVersion")
    }

    fun checkValidBuildCacheConfiguration() {
        !jdkFor(JavaVersion.VERSION_1_7).current && jdkFor(JavaVersion.VERSION_1_8).current
    }
}
