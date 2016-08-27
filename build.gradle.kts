import org.gradle.api.publish.*
import org.gradle.api.publish.maven.*
import org.gradle.jvm.tasks.Jar

apply {
    plugin("kotlin")
    plugin("maven-publish")
    plugin("com.jfrog.artifactory")
}

group = "org.gradle"

version = "0.3.2-SNAPSHOT"

val kotlinVersion by extra.properties

dependencies {
    compileOnly(gradleApi())
    // compileOnly(gradle("core"))
    // compileOnly(gradle("process-services"))
    // compileOnly(gradle("tooling-api"))

    compile("org.codehaus.groovy:groovy-all:2.4.7")
    compile("org.slf4j:slf4j-api:1.7.10")
    compile("javax.inject:javax.inject:1")
    compile("org.ow2.asm:asm-all:5.1")

    compile(kotlin("stdlib"))
    compile(kotlin("reflect"))
    compile(kotlin("compiler-embeddable"))

    testCompile(gradleTestKit())
    testCompile("junit:junit:4.12")
    testCompile("com.nhaarman:mockito-kotlin:0.5.2")
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
        create<MavenPublication>("mavenJava") {
            from(components.getByName("java"))
        }
    }
}

fun gradle(module: String) = "org.gradle:gradle-$module:3.1-20160818000032+0000"

fun kotlin(module: String) = "org.jetbrains.kotlin:kotlin-$module:$kotlinVersion"
