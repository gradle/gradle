plugins {
    `java-platform`
}

description = "Provides a platform that constrains versions of external dependencies used by Gradle"

// Here you should declare versions which should be shared by the different modules of buildSrc itself
val javaParserVersion = "3.18.0"
val groovyVersion = "3.0.9"
val asmVersion = "9.2"
// To try out better kotlin compilation avoidance and incremental compilation
// with -Pkotlin.incremental.useClasspathSnapshot=true
val defaultBuildKotlinVersion = "1.7.0-dev-1904"

val kotlinVersion = providers.gradleProperty("buildKotlinVersion")
    .getOrElse(defaultBuildKotlinVersion)

dependencies {
    constraints {
        // Gradle Plugins
        api("com.gradle:gradle-enterprise-gradle-plugin:3.8.1")
        api("com.gradle.enterprise:test-distribution-gradle-plugin:2.2.3") // Sync with `settings.gradle.kts`
        api("org.gradle.guides:gradle-guides-plugin:0.19.1")
        api("com.gradle.publish:plugin-publish-plugin:0.20.0")
        api("gradle.plugin.org.jetbrains.gradle.plugin.idea-ext:gradle-idea-ext:1.0.1")
        api("me.champeau.gradle:japicmp-gradle-plugin:0.3.0")
        api("me.champeau.jmh:jmh-gradle-plugin:0.6.4")
        api("org.asciidoctor:asciidoctor-gradle-jvm:3.3.2")
        api("org.gradle:test-retry-gradle-plugin:1.3.1")
        api("org.jetbrains.kotlin:kotlin-gradle-plugin") { version { strictly(kotlinVersion) } }
        api(kotlin("compiler-embeddable")) { version { strictly(kotlinVersion) } }
        api("org.jlleitschuh.gradle:ktlint-gradle:10.1.0")
        api("org.gradle.kotlin:gradle-kotlin-dsl-conventions:0.7.0")
        api("com.autonomousapps:dependency-analysis-gradle-plugin:0.71.0")

        // Java Libraries
        api("com.github.javaparser:javaparser-core:$javaParserVersion")
        api("com.github.javaparser:javaparser-symbol-solver-core:$javaParserVersion")
        api("com.google.guava:guava:27.1-jre")
        api("com.google.errorprone:error_prone_annotations:2.5.1")
        api("com.google.code.gson:gson:2.8.6")
        api("com.nhaarman:mockito-kotlin:1.6.0")
        api("com.thoughtworks.qdox:qdox:2.0.0")
        api("com.uwyn:jhighlight:1.0")
        api("com.vladsch.flexmark:flexmark-all:0.34.60") {
            because("Higher versions tested are either incompatible (0.62.2) or bring additional unwanted dependencies (0.36.8)")
        }
        api("commons-io:commons-io:2.8.0")
        api("commons-lang:commons-lang:2.6")
        api("io.mockk:mockk:1.10.6")
        api("javax.activation:activation:1.1.1")
        api("javax.xml.bind:jaxb-api:2.3.1")
        api("com.sun.xml.bind:jaxb-core:2.2.11")
        api("com.sun.xml.bind:jaxb-impl:2.2.11")
        api("junit:junit:4.13.2")
        api("org.spockframework:spock-core:2.1-groovy-3.0")
        api("org.spockframework:spock-junit4:2.1-groovy-3.0")
        api("org.asciidoctor:asciidoctorj:2.4.3")
        api("org.asciidoctor:asciidoctorj-pdf:1.5.4")
        api("com.beust:jcommander:1.78")
        api("org.codehaus.groovy:$groovyVersion")
        api("org.codehaus.groovy.modules.http-builder:http-builder:0.7.2")
        api("org.codenarc:CodeNarc:2.0.0")
        api("org.eclipse.jgit:org.eclipse.jgit:5.7.0.202003110725-r")
        api("org.javassist:javassist:3.27.0-GA")
        api("org.jetbrains.kotlinx:kotlinx-metadata-jvm:0.4.1")
        api("org.jsoup:jsoup:1.13.1")
        api("org.junit.jupiter:junit-jupiter:5.8.2")
        api("org.junit.vintage:junit-vintage-engine:5.8.2")
        api("org.openmbee.junit:junit-xml-parser:1.0.0")
        api("org.ow2.asm:asm:$asmVersion")
        api("org.ow2.asm:asm-commons:$asmVersion")
        api("xerces:xercesImpl:2.12.1") {
            because("Maven Central and JCenter disagree on version 2.9.1 metadata")
        }
        api("net.bytebuddy:byte-buddy") { version { strictly("1.10.21") } }
        api("org.objenesis:objenesis") { version { strictly("3.1") } }
    }
}
