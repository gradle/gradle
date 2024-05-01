import gradlebuild.integrationtests.model.GradleDistribution
import org.asciidoctor.gradle.jvm.AsciidoctorTask
import org.gradle.docs.internal.tasks.CheckLinks
import org.gradle.docs.samples.internal.tasks.InstallSample
import org.gradle.internal.os.OperatingSystem

import javax.inject.Inject

import gradlebuild.basics.repoRoot
import gradlebuild.basics.configurationCacheEnabledForDocsTests
import gradlebuild.basics.runBrokenForConfigurationCacheDocsTests
import gradlebuild.basics.googleApisJs
import gradlebuild.basics.maxParallelForks

plugins {
    id("gradlebuild.internal.java")
    // TODO: Apply asciidoctor in documentation plugin instead.
    id("org.asciidoctor.jvm.convert")
    id("gradlebuild.documentation")
    id("gradlebuild.generate-samples")
    id("gradlebuild.split-docs")
}

repositories {
    googleApisJs()
}

configurations {
    create("gradleFullDocsElements") {
        attributes {
            attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
            attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.DOCUMENTATION))
            attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named("gradle-documentation"))
        }
        isVisible = false
        isCanBeResolved = false
        isCanBeConsumed = true
    }
    named("docsTestRuntimeClasspath") {
        extendsFrom(configurations.docsTestRuntimeClasspath.get())
    }
}

dependencies {
    // generate Javadoc for the full Gradle distribution
    runtimeOnly(project(":distributions-full"))

    userGuideTask("xalan:xalan:2.7.1")
    userGuideTask("xerces:xercesImpl:2.11.0")
    userGuideTask("net.sf.xslthl:xslthl:2.0.1")

    userGuideStyleSheets("net.sf.docbook:docbook-xsl:1.75.2:resources@zip")

    jquery("jquery:jquery.min:3.5.1@js")

    testImplementation(project(":base-services"))
    testImplementation(project(":core"))
    testImplementation(libs.jsoup)
    testImplementation("org.gebish:geb-spock:2.2")
    testImplementation("org.seleniumhq.selenium:selenium-htmlunit-driver:2.42.2")
    testImplementation(libs.commonsHttpclient)
    testImplementation(libs.httpmime)

    docsTestImplementation(platform(project(":distributions-dependencies")))
    docsTestImplementation(project(":internal-integ-testing"))
    docsTestImplementation(project(":base-services"))
    docsTestImplementation(project(":logging"))
    docsTestImplementation(libs.junit5Vintage)
    docsTestImplementation(libs.junit)

    integTestDistributionRuntimeOnly(project(":distributions-full"))
}

configurations.docsTestRuntimeClasspath {
    exclude("org.slf4j","slf4j-simple")
}

asciidoctorj {
    modules.pdf.version("2.3.10")
    setVersion("2.5.11") // DOES THIS WORK?
}

tasks.withType<AsciidoctorTask>().configureEach {
    if (name == "userguideSinglePagePdf") {
        asciidoctorj.docExtensions(
            project.getDependencies().create(project(":docs-asciidoctor-extensions-base")),
        )
    } else {
        asciidoctorj.docExtensions(
            project.getDependencies().create(project(":docs-asciidoctor-extensions")),
            project.getDependencies().create(project.files("src/main/resources"))
        )
    }
}

gradleDocumentation {
    javadocs {
        javaApi = project.uri("https://docs.oracle.com/javase/8/docs/api")
        groovyApi = project.uri("https://docs.groovy-lang.org/docs/groovy-${libs.groovyVersion}/html/gapi")
    }
    kotlinDslReference {
        dokkaVersionOverride = "1.9.10"
    }
}

/*tasks.named("stageDocs") {
    // Add samples to generated documentation
    from(samples.distribution.renderedDocumentation) {
        into("samples")
    }
}*/

samples {
    templates {
        //javaAndroidApplication
    }
}

// Use the version of Gradle being built, not the version of Gradle used to build,
// also don't validate distribution url, since it is just a local distribution
tasks.named<Wrapper>("generateWrapperForSamples") {
    gradleVersion = project.version.toString()
    validateDistributionUrl = false
}

// TODO: The rich console to plain text is flaky
tasks.named("checkAsciidoctorSampleContents") {
    enabled = false
}

tasks.withType<InstallSample>().configureEach {
    if (name.contains("ForTest")) {
        excludes.add("gradle/wrapper/**")
        excludes.add("README")
    }
}

tasks.named("quickTest") {
    dependsOn("checkDeadInternalLinks")
}

tasks.named("docsTest") {
    maxParallelForks = 2
    // The org.gradle.samples plugin uses Exemplar to execute integration tests on the samples.
    // Exemplar doesn't know about that it's running in the context of the gradle/gradle build
    // so it uses the Gradle distribution from the running build. This is not correct, because
    // we want to verify that the samples work with the Gradle distribution being built.

}

configurations {
    docsTestRuntimeClasspath {
        exclude(group = "org.slf4j", module = "slf4j-simple")
    }
}

asciidoctorj {
    //version = "2.5.11"
    modules {
        pdf.version("2.3.10")
    }
}

// TODO: The rich console to plain text is flaky
tasks.named("checkAsciidoctorSampleContents") {
    enabled = false
}

tasks.named("quickTest") {
    dependsOn("checkDeadInternalLinks")
}
