import org.gradle.build.ClasspathManifest
import org.gradle.build.DefaultJavaInstallation
import org.gradle.internal.jvm.Jvm
import org.gradle.jvm.toolchain.internal.JavaInstallationProbe
import org.gradle.plugins.compile.AvailableJavaInstallations
import org.gradle.testing.DistributionTest
import org.gradle.BuildEnvironment.isCiServer

import org.gradle.kotlin.dsl.support.serviceOf

import java.util.concurrent.Callable
import java.util.jar.Attributes

apply { plugin("groovy") }
apply { plugin("gradle-compile") }

val base = the<BasePluginConvention>()
val java = the<JavaPluginConvention>()

base.archivesBaseName = "gradle-${name.replace(Regex("\\p{Upper}")) { "-${it.value.toLowerCase()}" }}"

java.sourceCompatibility = JavaVersion.VERSION_1_7

val javaInstallationProbe = gradle.serviceOf<JavaInstallationProbe>()

val compileTasks by extra { tasks.matching { it is JavaCompile || it is GroovyCompile } }
val testTasks by extra { tasks.withType<Test>() }
val generatedResourcesDir by extra { file("$buildDir/generated-resources/main") }
val generatedTestResourcesDir by extra { file("$buildDir/generated-resources/test") }
val jarTasks by extra { tasks.withType<Jar>() }
val javaInstallationForTest = rootProject.the<AvailableJavaInstallations>().javaInstallationForTest

dependencies {
    val testCompile by configurations
    testCompile(library("junit"))
    testCompile(library("groovy"))
    testCompile(testLibrary("spock"))
    testLibraries("jmock").forEach { testCompile(it) }

    components {
        withModule("org.spockframework:spock-core") {
            allVariants {
                withDependencyConstraints {
                    filter { it.group == "org.objenesis" }.forEach {
                        it.version { prefer("1.2") }
                        it.because("1.2 is required by Gradle and part of the distribution")
                    }
                }
            }
        }
    }
}

val classpathManifest by tasks.creating(ClasspathManifest::class)

java.sourceSets["main"].output.dir(mapOf("builtBy" to classpathManifest), generatedResourcesDir)

class CiEnvironmentProvider(private val test: Test, private val rootProject: Project) : CommandLineArgumentProvider, Named {
    override fun getName() = "ciEnvironment"

    override fun asArguments(): Iterable<String> {
        return if (isCiServer) {
            mapOf(
                "org.gradle.test.maxParallelForks" to test.maxParallelForks,
                "org.gradle.ci.agentCount" to 2,
                "org.gradle.ci.agentNum" to rootProject.extra["agentNum"]
            ).map {
                "-D${it.key}=${it.value}"
            }
        } else {
            listOf()
        }
    }
}

testTasks.all {
    maxParallelForks = rootProject.extra["maxParallelForks"] as Int
    jvmArgumentProviders.add(CiEnvironmentProvider(this, rootProject))
    executable = Jvm.forHome(javaInstallationForTest.javaHome).javaExecutable.absolutePath
    environment["JAVA_HOME"] = javaInstallationForTest.javaHome.absolutePath
    if (javaInstallationForTest.javaVersion.isJava7) {
        // enable class unloading
        jvmArgs("-XX:+UseConcMarkSweepGC", "-XX:+CMSClassUnloadingEnabled")
    }
    // Includes JVM vendor and major version
    inputs.property("javaInstallation", javaInstallationForTest.displayName)
    doFirst {
        if (isCiServer) {
            println("maxParallelForks for '$path' is $maxParallelForks")
        }
    }
}

jarTasks.all {
    version = rootProject.extra["baseVersion"] as String
    manifest.attributes(mapOf(
        Attributes.Name.IMPLEMENTATION_TITLE.toString() to "Gradle",
        Attributes.Name.IMPLEMENTATION_VERSION.toString() to version))
}

apply {
    plugin("test-fixtures")

    if (file("src/integTest").isDirectory) {
        from("$rootDir/gradle/integTest.gradle.kts")
    }

    if (file("src/crossVersionTest").isDirectory) {
        from("$rootDir/gradle/crossVersionTest.gradle")
    }

    if (file("src/performanceTest").isDirectory) {
        plugin("performance-test")
    }

    if (file("src/jmh").isDirectory) {
        from("$rootDir/gradle/jmh.gradle")
    }

    from("$rootDir/gradle/distributionTesting.gradle.kts")
    from("$rootDir/gradle/intTestImage.gradle")
}

val compileAll by tasks.creating {
    dependsOn(compileTasks)
}
