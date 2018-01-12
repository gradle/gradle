import org.gradle.api.internal.GradleInternal
import org.gradle.build.ClasspathManifest
import org.gradle.build.DefaultJavaInstallation
import org.gradle.internal.jvm.Jvm
import org.gradle.jvm.toolchain.internal.JavaInstallationProbe
import org.gradle.testing.DistributionTest

import java.util.concurrent.Callable
import java.util.jar.Attributes

apply { plugin("groovy") }

val base = the<BasePluginConvention>()
val java = the<JavaPluginConvention>()

base.archivesBaseName = "gradle-${name.replace(Regex("\\p{Upper}")) { "-${it.value.toLowerCase()}" }}"

java.sourceCompatibility = JavaVersion.VERSION_1_7

val javaInstallationProbe = (gradle as GradleInternal).services.get(JavaInstallationProbe::class.java)

val compileTasks by extra { tasks.matching { it is JavaCompile || it is GroovyCompile } }
val testTasks by extra { tasks.withType<Test>() }
val javaInstallationForTest = DefaultJavaInstallation()
val generatedResourcesDir by extra { file("$buildDir/generated-resources/main") }
val generatedTestResourcesDir by extra { file("$buildDir/generated-resources/test") }
val jarTasks by extra { tasks.withType<Jar>() }

if (!hasProperty("testJavaHome")) {
    extra["testJavaHome"] = System.getProperty("testJavaHome")
}
val testJavaHome: String? by extra

when (testJavaHome) {
    is String -> {
        val testJavaHomeFile = File(testJavaHome)
        javaInstallationForTest.javaHome = testJavaHomeFile
        javaInstallationProbe.checkJdk(testJavaHomeFile).configure(javaInstallationForTest)
    }
    else -> {
        val jvm: Jvm by rootProject.extra
        javaInstallationForTest.javaHome = jvm.javaHome
        javaInstallationProbe.current(javaInstallationForTest)
    }
}

dependencies {
    val testCompile by configurations
    testCompile(library("junit"))
    testCompile(library("groovy"))
    libraries("jmock").forEach { testCompile(it) }
    libraries("spock").forEach { testCompile(it) }
}

// Extracted as it's also used by buildSrc
apply { from("$rootDir/gradle/compile.gradle") }

val classpathManifest by tasks.creating(ClasspathManifest::class)

java.sourceSets["main"].output.dir(mapOf("builtBy" to classpathManifest), generatedResourcesDir)

val isCiServer: Boolean by rootProject.extra

testTasks.all {
    maxParallelForks = rootProject.extra["maxParallelForks"] as Int
    if (isCiServer) {
        val ciProperties = mapOf(
            "org.gradle.test.maxParallelForks" to maxParallelForks,
            "org.gradle.ci.agentCount" to 2,
            "org.gradle.ci.agentNum" to rootProject.extra["agentNum"])
        systemProperties(ciProperties)

        // Ignore Forking/agentNum properties in order to be able to pull tests
        if (this is DistributionTest) {
            ciProperties.keys.forEach { ignoreSystemProperty(it) }
        } else {
            inputs.property("systemProperties", Callable<Any> { systemProperties - ciProperties })
        }
    }
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
        from("$rootDir/gradle/performanceTest.gradle")
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
