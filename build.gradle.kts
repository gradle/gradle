import org.gradle.api.artifacts.dsl.*
import org.gradle.api.plugins.*
import org.gradle.api.publish.*
import org.gradle.api.publish.maven.*
import org.gradle.jvm.tasks.Jar
import org.gradle.script.lang.kotlin.*

apply {
    plugin("kotlin")
    plugin("maven-publish")
    plugin("com.jfrog.artifactory")
}

group = "org.gradle"

version = "1.0.0-SNAPSHOT"

val kotlinVersion = extra["kotlinVersion"] as String

dependencies {
    compileOnly(gradle("core"))
    compileOnly(gradle("process-services"))
    compileOnly(gradle("tooling-api"))

    compile("org.codehaus.groovy:groovy-all:2.4.6")
    compile("org.slf4j:slf4j-api:1.7.10")
    compile("javax.inject:javax.inject:1")
    compile("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
    compile("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")
    compile("org.jetbrains.kotlin:kotlin-compiler-embeddable:$kotlinVersion")

    testCompile(gradleTestKit())
    testCompile("junit:junit:4.12")
}

tasks.withType<Jar> {
    from(the<JavaPluginConvention>().sourceSets.getByName("main").allSource)
    manifest.attributes.apply {
        put("Implementation-Title", "Gradle Script Kotlin")
        put("Implementation-Version", version)
    }
}

configure<PublishingExtension> {
    publications {
        it.create<MavenPublication>("mavenJava") {
            from(components.getByName("java"))
        }
    }
}

fun DependencyHandler.compileOnly(descriptor: Any) = add("compileOnly", descriptor)

fun DependencyHandler.compile(descriptor: Any) = add("compile", descriptor)

fun DependencyHandler.testCompile(descriptor: Any) = add("testCompile", descriptor)

fun DependencyHandler.testRuntime(descriptor: Any) = add("testRuntime", descriptor)

fun gradle(module: String) = "org.gradle:gradle-$module:3.+"
