package org.gradle.plugins.compile

import org.gradle.api.JavaVersion
import org.gradle.internal.jvm.Jvm
import org.gradle.jvm.toolchain.internal.JavaInstallationProbe
import org.gradle.jvm.toolchain.internal.LocalJavaInstallation
import java.io.File

class DefaultJavaInstallation(val current: Boolean, private val javaHome: File) : LocalJavaInstallation {
    private
    lateinit var name: String
    private
    lateinit var javaVersion: JavaVersion
    private
    lateinit var displayName: String

    override fun getName() = name
    override fun getDisplayName() = displayName
    override fun setDisplayName(displayName: String) { this.displayName = displayName }
    override fun getJavaVersion() = javaVersion
    override fun setJavaVersion(javaVersion: JavaVersion) { this.javaVersion = javaVersion }
    override fun getJavaHome() = javaHome
    override fun setJavaHome(javaHome: File) { throw UnsupportedOperationException("JavaHome cannot be changed") }
    val toolsJar: File? by lazy { Jvm.forHome(javaHome).toolsJar }

    override fun toString(): String = "${displayName} (${javaHome.absolutePath})"
}

open class AvailableJavaInstallations(javaHomesForCompilation: List<String>, javaHomeForTest: String?, private val javaInstallationProbe: JavaInstallationProbe) {
    private val javaInstallations: Map<JavaVersion, DefaultJavaInstallation> = findJavaInstallations(javaHomesForCompilation)
    val currentJavaInstallation: DefaultJavaInstallation
    val javaInstallationForTest: DefaultJavaInstallation

    init {
        currentJavaInstallation = DefaultJavaInstallation(true, Jvm.current().javaHome).apply {
            javaInstallationProbe.current(this)
        }
        javaInstallationForTest = when (javaHomeForTest) {
            null -> currentJavaInstallation
            else -> detectJavaInstallation(javaHomeForTest)
        }
    }

    fun jdkForCompilation(javaVersion: JavaVersion) = when {
        javaVersion == currentJavaInstallation.javaVersion -> currentJavaInstallation
        javaInstallations.containsKey(javaVersion) -> javaInstallations[javaVersion]!!
        javaVersion <= currentJavaInstallation.javaVersion -> currentJavaInstallation
        else -> throw IllegalArgumentException("No Java installation found which supports Java version $javaVersion")
    }

    val validBuildCacheConfiguration
        get() = !jdkForCompilation(JavaVersion.VERSION_1_7).current && jdkForCompilation(JavaVersion.VERSION_1_8).current

    private
    fun findJavaInstallations(javaHomes: List<String>) =
        javaHomes.map(::detectJavaInstallation).associateBy { it.javaVersion }

    private
    fun detectJavaInstallation(javaHomePath: String) =
        DefaultJavaInstallation(false, File(javaHomePath)).apply {
            javaInstallationProbe.checkJdk(javaHome).configure(this)
        }
}
