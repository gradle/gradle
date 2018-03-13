package org.gradle.gradlebuild.java

import org.gradle.api.GradleException
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.caching.configuration.BuildCacheConfiguration
import org.gradle.internal.jvm.Jvm
import org.gradle.jvm.toolchain.internal.JavaInstallationProbe
import org.gradle.jvm.toolchain.internal.LocalJavaInstallation
import org.slf4j.LoggerFactory
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

    override fun toString(): String = "$displayName (${javaHome.absolutePath})"
}


private
const val java7HomePropertyName = "java7Home"


private
const val testJavaHomePropertyName = "testJavaHome"


private
const val oracleJdk8 = "Oracle JDK 8"


private
const val oracleJdk7 = "Oracle JDK 7"


open class AvailableJavaInstallations(project: Project, private val javaInstallationProbe: JavaInstallationProbe) {
    private
    val logger = LoggerFactory.getLogger(AvailableJavaInstallations::class.java)
    private
    val javaInstallations: Map<JavaVersion, DefaultJavaInstallation>

    val currentJavaInstallation: DefaultJavaInstallation
    val javaInstallationForTest: DefaultJavaInstallation

    init {
        val resolvedJava7Home = resolveJavaHomePath(java7HomePropertyName, project)
        val javaHomesForCompilation = listOfNotNull(resolvedJava7Home)
        val javaHomeForTest = resolveJavaHomePath(testJavaHomePropertyName, project)
        javaInstallations = findJavaInstallations(javaHomesForCompilation)
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

    fun validateBuildCacheConfiguration(buildCacheConfiguration: BuildCacheConfiguration) {
        val validationErrors = validateCompilationJdks()
        if (validationErrors.isNotEmpty()) {
            val message = formatValidationError(
                "In order to have cache hits from the remote build cache, your environment needs to be configured accordingly!",
                validationErrors
            )
            if (buildCacheConfiguration.remote?.isEnabled == true) {
                throw GradleException(message)
            } else {
                logger.warn(message)
            }
        }
    }

    fun validateProductionEnvironment() {
        val validationErrors = validateCompilationJdks() +
            mapOf(
                validationMessage(testJavaHomePropertyName, javaInstallationForTest, oracleJdk8) to (javaInstallationForTest.displayName != oracleJdk8)
            ).filterValues { it }.keys
        if (validationErrors.isNotEmpty()) {
            throw GradleException(formatValidationError("JDKs not configured correctly for production build.", validationErrors))
        }
    }

    private
    fun validateCompilationJdks(): Collection<String> {
        val jdkForCompilation = javaInstallations.values.firstOrNull()
        return mapOf(
            "Must set project or system property '$java7HomePropertyName' to the path of an $oracleJdk7, is currently unset." to (jdkForCompilation == null),
            validationMessage(java7HomePropertyName, jdkForCompilation, oracleJdk7) to (jdkForCompilation != null && jdkForCompilation.displayName != oracleJdk7),
            "Must use Oracle JDK 8 to perform this build. Is currently ${currentJavaInstallation.displayName} at ${currentJavaInstallation.javaHome}." to
                (currentJavaInstallation.displayName != oracleJdk8)
        ).filterValues { it }.keys
    }

    private
    fun formatValidationError(mainMessage: String, validationErrors: Collection<String>): String =
        (listOf(mainMessage, "Problems found:") +
            validationErrors.map {
                "    - $it"
            }).joinToString("\n")

    private
    fun validationMessage(propertyName: String, javaInstallation: DefaultJavaInstallation?, requiredVersion: String) =
        "Must set project or system property '$propertyName' to the path of an $requiredVersion, is currently ${javaInstallation?.displayName} at ${javaInstallation?.javaHome}."

    private
    fun findJavaInstallations(javaHomes: List<String>) =
        javaHomes.map(::detectJavaInstallation).associateBy { it.javaVersion }

    private
    fun detectJavaInstallation(javaHomePath: String) =
        DefaultJavaInstallation(false, File(javaHomePath)).apply {
            javaInstallationProbe.checkJdk(javaHome).configure(this)
        }

    private
    fun resolveJavaHomePath(propertyName: String, project: Project): String? = when {
        project.hasProperty(propertyName) -> project.property(propertyName) as String
        System.getProperty(propertyName) != null -> System.getProperty(propertyName)
        else -> null
    }
}
