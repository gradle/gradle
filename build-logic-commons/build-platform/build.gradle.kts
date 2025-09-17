import groovy.lang.GroovySystem
import org.gradle.util.internal.VersionNumber

plugins {
    `java-platform`
}

group = "gradlebuild"

description = "Provides a platform that constrains versions of external dependencies used by Gradle"

// Here you should declare versions which should be shared by the different modules of buildSrc itself
val javaParserVersion = "3.18.0"
// Note: this currently still contains 3/4 logic as we will temporarily have Groovy 3 for the build itself until we move to a Gradle built with Groovy 4
// It can be removed or changed to 4/5 logic (if necessary) at that point.
val groovyVersion = GroovySystem.getVersion()
val isGroovy4 = VersionNumber.parse(groovyVersion).major >= 4
val codenarcVersion = if (isGroovy4) "3.6.0-groovy-4.0" else "3.6.0"
val spockVersion = if (isGroovy4) "2.3-groovy-4.0" else "2.3-groovy-3.0"
val groovyGroup = if (isGroovy4) "org.apache.groovy" else "org.codehaus.groovy"
val asmVersion = "9.8"
// To try out newer kotlin versions
val kotlinVersion = providers.gradleProperty("buildKotlinVersion")
    .getOrElse(embeddedKotlinVersion)

dependencies {
    constraints {
        api("org.gradle.guides:gradle-guides-plugin:0.24.0")
        api("org.apache.ant:ant:1.10.15") // Bump the version brought in transitively by gradle-guides-plugin
        api("com.gradle:develocity-gradle-plugin:4.2") // Run `java build-logic-settings/UpdateDevelocityPluginVersion.java <new-version>` to update
        api("com.gradle.publish:plugin-publish-plugin:1.3.1")
        api("gradle.plugin.org.jetbrains.gradle.plugin.idea-ext:gradle-idea-ext:1.3")
        api("me.champeau.gradle:japicmp-gradle-plugin:0.4.1")
        api("me.champeau.jmh:jmh-gradle-plugin:0.7.2")
        api("org.asciidoctor:asciidoctor-gradle-jvm:4.0.2")
        api("org.jetbrains.kotlin:kotlin-gradle-plugin") { version { strictly(kotlinVersion) } }
        api(kotlin("compiler-embeddable")) { version { strictly(kotlinVersion) } }
        api("com.autonomousapps:dependency-analysis-gradle-plugin:3.0.1")

        // Java Libraries
        api("com.github.javaparser:javaparser-core:$javaParserVersion")
        api("com.github.javaparser:javaparser-symbol-solver-core:$javaParserVersion")
        api("com.google.guava:guava:33.4.6-jre")
        api("com.google.errorprone:error_prone_annotations:2.5.1")
        api("com.google.code.gson:gson:2.13.1") // keep in sync with settings.gradle.kts
        api("org.mockito.kotlin:mockito-kotlin:5.4.0")
        api("com.thoughtworks.qdox:qdox:2.0.3")
        api("com.uwyn:jhighlight:1.0")
        api("com.vladsch.flexmark:flexmark-all:0.34.60") {
            because("Higher versions tested are either incompatible (0.62.2) or bring additional unwanted dependencies (0.36.8)")
        }
        api("org.apache.pdfbox:pdfbox:2.0.24") {
            because("Flexmark 0.34.60 brings in a vulnerable version of pdfbox")
        }
        api("com.google.code.findbugs:jsr305:3.0.2")
        api("org.jspecify:jspecify:1.0.0")
        api("commons-io:commons-io:2.14.0")
        api("org.apache.commons:commons-lang3:3.17.0")
        api("javax.activation:activation:1.1.1")
        api("jakarta.xml.bind:jakarta.xml.bind-api:4.0.2")
        api("com.sun.xml.bind:jaxb-impl:4.0.5")
        api("junit:junit:4.13.2")
        api("org.spockframework:spock-core:$spockVersion")
        api("org.spockframework:spock-junit4:$spockVersion")
        api("org.asciidoctor:asciidoctorj:2.5.13")
        api("org.asciidoctor:asciidoctorj-api:2.5.13")
        api("org.jetbrains.dokka:dokka-gradle-plugin:2.0.0")
        api("com.fasterxml.woodstox:woodstox-core:6.4.0") {
            because("CVE-2022-40152 on lower versions")
        }
        api("com.beust:jcommander:1.78")
        api("$groovyGroup:groovy:$groovyVersion")
        api("org.codenarc:CodeNarc:$codenarcVersion")
        api("org.eclipse.jgit:org.eclipse.jgit:7.2.1.202505142326-r")
        api("org.javassist:javassist:3.30.2-GA")
        api("org.jetbrains.kotlin:kotlin-metadata-jvm:$kotlinVersion")
        api("org.jsoup:jsoup:1.15.3")
        api("org.junit.jupiter:junit-jupiter:5.8.2")
        api("org.junit.vintage:junit-vintage-engine:5.8.2")
        api("org.ow2.asm:asm:$asmVersion")
        api("org.ow2.asm:asm-commons:$asmVersion")
        api("org.ow2.asm:asm-tree:$asmVersion")
        api("xerces:xercesImpl:2.12.2") {
            because("Maven Central and JCenter disagree on version 2.9.1 metadata")
        }
        api("net.bytebuddy:byte-buddy") { version { strictly("1.17.5") } }
        api("org.objenesis:objenesis") { version { strictly("3.1") } }
    }
}
