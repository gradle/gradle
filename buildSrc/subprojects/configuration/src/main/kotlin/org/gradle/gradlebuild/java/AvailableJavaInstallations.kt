package org.gradle.gradlebuild.java

import org.gradle.api.GradleException
import org.gradle.api.JavaVersion
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.internal.concurrent.CompositeStoppable
import org.gradle.internal.jvm.JavaInfo
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.jvm.inspection.CachingJvmVersionDetector
import org.gradle.internal.jvm.inspection.DefaultJvmVersionDetector
import org.gradle.internal.nativeintegration.services.NativeServices
import org.gradle.internal.service.ServiceRegistryBuilder
import org.gradle.internal.service.scopes.GlobalScopeServices
import org.gradle.jvm.toolchain.internal.JavaInstallationProbe
import org.gradle.jvm.toolchain.internal.LocalJavaInstallation
import org.gradle.process.internal.ExecActionFactory
import org.gradle.process.internal.ExecHandleFactory
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
    override fun setDisplayName(displayName: String) {
        this.displayName = displayName
    }

    override fun getJavaVersion() = javaVersion
    override fun setJavaVersion(javaVersion: JavaVersion) {
        this.javaVersion = javaVersion
    }

    override fun getJavaHome() = javaHome
    override fun setJavaHome(javaHome: File) {
        throw UnsupportedOperationException("JavaHome cannot be changed")
    }
}


const val testJavaHomePropertyName = "testJavaHome"


private
const val productionJdkName = "AdoptOpenJDK 11"


interface AvailableJavaInstallationsParameters : BuildServiceParameters {
    var testJavaProperty: String?
}


abstract class AvailableJavaInstallations : BuildService<AvailableJavaInstallationsParameters>, AutoCloseable {
    // TODO - extract a public service for locating JVM/JDK instances and querying their metadata
    private
    val services = ServiceRegistryBuilder.builder().parent(NativeServices.getInstance()).provider(GlobalScopeServices(false)).build()
    private
    val jvmVersionDetector = CachingJvmVersionDetector(DefaultJvmVersionDetector(services.get(ExecHandleFactory::class.java)))
    private
    val javaInstallationProbe = JavaInstallationProbe(services.get(ExecActionFactory::class.java))

    val currentJavaInstallation: JavaInstallation = JavaInstallation(true, Jvm.current(), JavaVersion.current(), javaInstallationProbe).also {
        println("-> CURRENT JVM = ${it.javaHome}")
    }
    val javaInstallationForTest by lazy {
        determineJavaInstallation(testJavaHomePropertyName, parameters.testJavaProperty).also {
            println("-> TEST JVM = ${it.javaHome}")
        }
    }
    val javaInstallationForCompilation by lazy {
        determineJavaInstallationForCompilation().also {
            println("-> COMPILE JVM = ${it.javaHome}")
        }
    }

    override fun close() {
        println("-> CLOSING INSTALLATIONS")
        CompositeStoppable.stoppable(services).stop()
    }

    private
    fun determineJavaInstallationForCompilation() = currentJavaInstallation

    private
    fun determineJavaInstallation(propertyName: String, overrideValue: String?): JavaInstallation {
        val resolvedJavaHome = resolveJavaHomePath(propertyName, overrideValue)
        return when (resolvedJavaHome) {
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
            "Must use JDK 9+ to perform compilation in this build. It's currently ${javaInstallationForCompilation.vendorAndMajorVersion} at ${javaInstallationForCompilation.javaHome}." to
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
    fun resolveJavaHomePath(propertyName: String, overrideValue: String?): String? = when {
        // TODO:instant-execution - these should be marked as a build input in some way
        overrideValue != null -> overrideValue
        System.getProperty(propertyName) != null -> System.getProperty(propertyName)
        System.getenv(propertyName) != null -> System.getenv(propertyName)
        else -> null
    }
}
