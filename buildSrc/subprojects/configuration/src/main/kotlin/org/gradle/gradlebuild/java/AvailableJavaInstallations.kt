package org.gradle.gradlebuild.java

import org.gradle.api.GradleException
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.internal.jvm.JavaInfo
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.jvm.inspection.JvmVersionDetector
import org.gradle.jvm.toolchain.internal.JavaInstallationProbe
import org.gradle.jvm.toolchain.internal.LocalJavaInstallation
import org.slf4j.LoggerFactory
import java.io.File


class JavaInstallation(val current: Boolean, val jvm: JavaInfo, val javaVersion: JavaVersion, private val javaInstallationProbe: JavaInstallationProbe) {
    val javaHome = jvm.javaHome

    override fun toString(): String = "$vendorAndMajorVersion (${javaHome.absolutePath})"

    val toolsJar: File? by lazy { jvm.toolsJar }
    val vendorAndMajorVersion: String by lazy {
        ProbedLocalJavaInstallation(jvm.javaHome).apply {
            javaInstallationProbe.checkJdk(jvm.javaHome).configure(this)
        }.displayName
    }
}


private
class ProbedLocalJavaInstallation(private val javaHome: File) : LocalJavaInstallation {

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
}


private
const val java9HomePropertyName = "java9Home"


private
const val testJavaHomePropertyName = "testJavaHome"


private
const val oracleJdk9 = "Oracle JDK 9"


private
const val oracleJdk8 = "Oracle JDK 8"


private
const val oracleJdk7 = "Oracle JDK 7"


open class AvailableJavaInstallations(project: Project, private val javaInstallationProbe: JavaInstallationProbe, private val jvmVersionDetector: JvmVersionDetector) {
    private
    val logger = LoggerFactory.getLogger(AvailableJavaInstallations::class.java)
    private
    val javaInstallations: Map<JavaVersion, JavaInstallation>

    val currentJavaInstallation: JavaInstallation
    val javaInstallationForTest: JavaInstallation

    init {
        val resolvedJava9Home = resolveJavaHomePath(java9HomePropertyName, project)
        require(resolvedJava9Home != null || JavaVersion.current().isJava9Compatible) { "Building gradle on Java 8 requires $java9HomePropertyName system property or project property" }
        val javaHomesForCompilation = listOfNotNull(resolvedJava9Home)
        val javaHomeForTest = resolveJavaHomePath(testJavaHomePropertyName, project)
        javaInstallations = findJavaInstallations(javaHomesForCompilation)
        currentJavaInstallation = JavaInstallation(true, Jvm.current(), JavaVersion.current(), javaInstallationProbe)
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

    fun validateForRemoteBuildCacheUsage() {
        val validationErrors = validateCompilationJdks()
        if (validationErrors.isNotEmpty()) {
            val message = formatValidationError(
                "In order to have cache hits from the remote build cache, your environment needs to be configured accordingly!",
                validationErrors
            )
            throw GradleException(message)
        }
    }

    fun validateForProductionEnvironment() {
        val validationErrors = validateCompilationJdks() +
            mapOf(
                validationMessage(testJavaHomePropertyName, javaInstallationForTest, oracleJdk8) to (javaInstallationForTest.vendorAndMajorVersion != oracleJdk8)
            ).filterValues { it }.keys
        if (validationErrors.isNotEmpty()) {
            throw GradleException(formatValidationError("JDKs not configured correctly for production build.", validationErrors))
        }
    }

    private
    fun validateCompilationJdks(): Collection<String> {
        val jdkForCompilation = javaInstallations.values.firstOrNull()
        return mapOf(
            "Must use Oracle JDK 8/9 to perform this build. Is currently ${currentJavaInstallation.vendorAndMajorVersion} at ${currentJavaInstallation.javaHome}." to
                (currentJavaInstallation.vendorAndMajorVersion != oracleJdk8 && currentJavaInstallation.vendorAndMajorVersion != oracleJdk9)
        ).filterValues { it }.keys
    }

    private
    fun formatValidationError(mainMessage: String, validationErrors: Collection<String>): String =
        (listOf(mainMessage, "Problems found:") +
            validationErrors.map {
                "    - $it"
            }).joinToString("\n")

    private
    fun validationMessage(propertyName: String, javaInstallation: JavaInstallation?, requiredVersion: String) =
        "Must set project or system property '$propertyName' to the path of an $requiredVersion, is currently ${javaInstallation?.vendorAndMajorVersion} at ${javaInstallation?.javaHome}."

    private
    fun findJavaInstallations(javaHomes: List<String>) =
        javaHomes.map(::detectJavaInstallation).associateBy { it.javaVersion }

    private
    fun detectJavaInstallation(javaHomePath: String) =
        Jvm.forHome(File(javaHomePath)).let {
            JavaInstallation(false, Jvm.forHome(File(javaHomePath)), jvmVersionDetector.getJavaVersion(it), javaInstallationProbe)
        }

    private
    fun resolveJavaHomePath(propertyName: String, project: Project): String? = when {
        project.hasProperty(propertyName) -> project.property(propertyName) as String
        System.getProperty(propertyName) != null -> System.getProperty(propertyName)
        else -> null
    }
}
