import org.gradle.api.artifacts.dsl.*
import org.gradle.api.plugins.*
import org.gradle.api.publish.*
import org.gradle.api.publish.maven.*
import org.gradle.jvm.tasks.Jar
import org.gradle.script.lang.kotlin.*

apply {
    it.plugin("kotlin")
    it.plugin("maven-publish")
    it.plugin("com.jfrog.artifactory")
}

group = "org.gradle"

version = "1.0.0-SNAPSHOT"

val kotlinVersion = extra["kotlinVersion"]

fun DependencyHandler.compileOnly(descriptor: Any) = add("compileOnly", descriptor)

fun DependencyHandler.compile(descriptor: Any) = add("compile", descriptor)

dependencies {
    compileOnly("org.gradle:gradle-core:3+")
    compileOnly("org.gradle:gradle-process-services:3+")
    compile("org.codehaus.groovy:groovy-all:2.4.6")
    compile("org.slf4j:slf4j-api:1.7.10")
    compile("javax.inject:javax.inject:1")
    compile("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
    compile("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")
    compile("org.jetbrains.kotlin:kotlin-compiler-embeddable:$kotlinVersion")
}

tasks.withType<Jar> {
    from(the<JavaPluginConvention>().sourceSets.getByName("main").allSource)
}

configure<PublishingExtension> {
    publications {
        it.create<MavenPublication>("mavenJava") {
            from(components.getByName("java"))
        }

        // val kotlinCompilerEmbeddable = tasks.getByName("repackageKotlinCompilerEmbeddable") as Zip
        // it.create<MavenPublication>(kotlinCompilerEmbeddable.baseName) {
        //     groupId = "org.jetbrains.kotlin"
        //     artifactId = name
        //     version = kotlinCompilerEmbeddable.version
        //     artifact(kotlinCompilerEmbeddable)
        // }
    }
}
