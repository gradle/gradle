package org.gradle.gradlebuild.java

import org.gradle.api.GradleException
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.internal.jvm.JavaInfo
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.jvm.inspection.JvmVersionDetector
import org.gradle.jvm.toolchain.internal.JavaInstallationProbe
import org.gradle.jvm.toolchain.internal.LocalJavaInstallation
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
const val productionJdkName = "AdoptOpenJDK 11"


open class AvailableJavaInstallations(
    project: Project,
    private val javaInstallationProbe: JavaInstallationProbe,
    private val jvmVersionDetector: JvmVersionDetector
) {
    val currentJavaInstallation: JavaInstallation = JavaInstallation(true, Jvm.current(), JavaVersion.current(), javaInstallationProbe)
    val javaInstallationForTest: JavaInstallation
    val javaInstallationForCompilation: JavaInstallation

    init {
        javaInstallationForTest = determineJavaInstallation(project, testJavaHomePropertyName)
        javaInstallationForCompilation = determineJavaInstallationForCompilation(project)
    }

    private
    fun determineJavaInstallationForCompilation(project: Project) = if (JavaVersion.current().isJava9Compatible) currentJavaInstallation else determineJavaInstallation(project, java9HomePropertyName)

    private
    fun determineJavaInstallation(project: Project, propertyName: String): JavaInstallation {
        return when (val resolvedJavaHome = resolveJavaHomePath(project, propertyName)) {
            null -> currentJavaInstallation
            else -> detectJavaInstallation(resolvedJavaHome)
        }
    }

    fun validateForCompilation() {
        validate(validateCompilationJdks())
    }

    fun validateForProductionEnvironment() {
        validate(validateProductionJdks())
    }

    private
    fun validate(errorMessages: Map<String, Boolean>) {
        val errors = errorMessages.filterValues { it }.keys
        if (errors.isNotEmpty()) {
            throw GradleException(formatValidationError("JDKs not configured correctly for the build.", errors))
        }
    }

    private
    fun validateCompilationJdks(): Map<String, Boolean> =
        mapOf(
            "Must use JDK 9+ to perform compilation in this build. It's currently ${javaInstallationForCompilation.vendorAndMajorVersion} at ${javaInstallationForCompilation.javaHome}. " +
                "You can either run the build on JDK 9+ or set a project, system property, or environment variable '$java9HomePropertyName' to a Java9-compatible JDK home path" to
                !javaInstallationForCompilation.javaVersion.isJava9Compatible
        )

    private
    fun validateProductionJdks(): Map<String, Boolean> =
        mapOf(
            "Must use $productionJdkName to perform this build. Is currently ${currentJavaInstallation.vendorAndMajorVersion} at ${currentJavaInstallation.javaHome}." to
                (currentJavaInstallation.vendorAndMajorVersion != productionJdkName)
        )

    private
    fun formatValidationError(mainMessage: String, validationErrors: Collection<String>): String =
        (listOf(mainMessage, "Problems found:") +
            validationErrors.map {
                "    - $it"
            }).joinToString("\n")

    private
    fun detectJavaInstallation(javaHomePath: String) =
        Jvm.forHome(File(javaHomePath)).let {
            JavaInstallation(false, Jvm.forHome(File(javaHomePath)), jvmVersionDetector.getJavaVersion(it), javaInstallationProbe)
        }

    private
    fun resolveJavaHomePath(project: Project, propertyNam: String): String? = when {
        project.hasProperty(propertyNam) -> project.property(propertyNam) as String
        System.getProperty(propertyNam) != null -> System.getProperty(propertyNam)
        System.getenv(propertyNam) != null -> System.getenv(propertyNam)
        else -> null
    }
}
