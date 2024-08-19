import groovy.lang.GroovySystem
import org.gradle.util.internal.VersionNumber

plugins {
    `java-platform`
}

group = "gradlebuild"

description = "Provides a platform that constrains versions of external dependencies used by Gradle"

// Here you should declare versions which should be shared by the different modules of buildSrc itself
val javaParserVersion = "3.18.0"
val groovyVersion = GroovySystem.getVersion()
val isGroovy4 = VersionNumber.parse(groovyVersion).major >= 4
val codenarcVersion = if (isGroovy4) "3.1.0-groovy-4.0" else "3.1.0"
val spockVersion = if (isGroovy4) "2.2-groovy-4.0" else "2.2-groovy-3.0"
val asmVersion = "9.7"
// To try out better kotlin compilation avoidance and incremental compilation
// with -Pkotlin.incremental.useClasspathSnapshot=true
val kotlinVersion = providers.gradleProperty("buildKotlinVersion")
    .getOrElse(embeddedKotlinVersion)

dependencies {
    constraints {
        api("org.gradle.guides:gradle-guides-plugin:0.23")
        api("org.apache.ant:ant:1.10.14") // Bump the version brought in transitively by gradle-guides-plugin
        api("com.gradle:develocity-gradle-plugin:3.17.6") // Run `build-logic-settings/update-develocity-plugin-version.sh <new-version>` to update
        api("com.gradle.publish:plugin-publish-plugin:1.2.1")
        api("gradle.plugin.org.jetbrains.gradle.plugin.idea-ext:gradle-idea-ext:1.0.1")
        api("me.champeau.gradle:japicmp-gradle-plugin:0.4.1")
        api("me.champeau.jmh:jmh-gradle-plugin:0.7.2")
        api("org.asciidoctor:asciidoctor-gradle-jvm:4.0.2")
        api("org.jetbrains.kotlin:kotlin-gradle-plugin") { version { strictly(kotlinVersion) } }
        api(kotlin("compiler-embeddable")) { version { strictly(kotlinVersion) } }
        api("org.jlleitschuh.gradle:ktlint-gradle:10.3.0")
        api("org.gradle.kotlin:gradle-kotlin-dsl-conventions:0.9.0")
        api("com.autonomousapps:dependency-analysis-gradle-plugin:1.31.0")
        api("com.squareup.okio:okio:3.4.0") {
            because("Bump version brought in by dependency-analysis-gradle-plugin, to resolve CVE-2022-3635")
        }

        // Java Libraries
        api("com.github.javaparser:javaparser-core:$javaParserVersion")
        api("com.github.javaparser:javaparser-symbol-solver-core:$javaParserVersion")
        api("com.google.guava:guava:32.1.2-jre")
        api("com.google.errorprone:error_prone_annotations:2.5.1")
        api("com.google.code.gson:gson:2.8.9")
        api("com.nhaarman:mockito-kotlin:1.6.0")
        api("com.thoughtworks.qdox:qdox:2.0.3")
        api("com.uwyn:jhighlight:1.0")
        api("com.vladsch.flexmark:flexmark-all:0.34.60") {
            because("Higher versions tested are either incompatible (0.62.2) or bring additional unwanted dependencies (0.36.8)")
        }
        api("org.apache.pdfbox:pdfbox:2.0.24") {
            because("Flexmark 0.34.60 brings in a vulnerable version of pdfbox")
        }
        api("com.google.code.findbugs:jsr305:3.0.2")
        api("commons-io:commons-io:2.8.0")
        api("commons-lang:commons-lang:2.6")
        api("javax.activation:activation:1.1.1")
        api("javax.xml.bind:jaxb-api:2.3.1")
        api("com.sun.xml.bind:jaxb-core:2.2.11")
        api("com.sun.xml.bind:jaxb-impl:2.2.11")
        api("junit:junit:4.13.2")
        api("org.spockframework:spock-core:$spockVersion")
        api("org.spockframework:spock-junit4:$spockVersion")
        api("org.asciidoctor:asciidoctorj:2.5.11")
        api("org.asciidoctor:asciidoctorj-api:2.5.11")
        api("org.asciidoctor:asciidoctorj-pdf:2.3.10")
        api("dev.adamko.dokkatoo:dokkatoo-plugin:2.3.1")
        api("org.jetbrains.dokka:dokka-core:1.9.20")
        api("com.fasterxml.woodstox:woodstox-core:6.4.0") {
            because("CVE-2022-40152 on lower versions")
        }
        api("com.beust:jcommander:1.78")
        api("org.codehaus.groovy:$groovyVersion")
        api("org.codehaus.groovy.modules.http-builder:http-builder:0.7.2") // TODO maybe change group name when upgrading to Groovy 4
        api("org.codenarc:CodeNarc:$codenarcVersion")
        api("org.eclipse.jgit:org.eclipse.jgit:5.13.3.202401111512-r")
        api("org.javassist:javassist:3.27.0-GA")
        api("org.jetbrains.kotlinx:kotlinx-metadata-jvm:0.7.0")
        api("org.jsoup:jsoup:1.15.3")
        api("org.junit.jupiter:junit-jupiter:5.8.2")
        api("org.junit.vintage:junit-vintage-engine:5.8.2")
        api("org.openmbee.junit:junit-xml-parser:1.0.0")
        api("org.ow2.asm:asm:$asmVersion")
        api("org.ow2.asm:asm-commons:$asmVersion")
        api("org.ow2.asm:asm-tree:$asmVersion")
        api("xerces:xercesImpl:2.12.2") {
            because("Maven Central and JCenter disagree on version 2.9.1 metadata")
        }
        api("net.bytebuddy:byte-buddy") { version { strictly("1.10.21") } }
        api("org.objenesis:objenesis") { version { strictly("3.1") } }
    }
}
