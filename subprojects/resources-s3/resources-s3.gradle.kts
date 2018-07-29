import org.gradle.gradlebuild.java.AvailableJavaInstallations
import org.gradle.gradlebuild.testing.integrationtests.cleanup.WhenNotEmpty
import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    id("java-library")
    id("gradlebuild.strict-compile")
    id("gradlebuild.classycle")
}

dependencies {
    api(project(":core"))
    api(project(":resources"))

    implementation(project(":resourcesHttp"))
    implementation(library("slf4j_api"))
    implementation(library("guava"))
    implementation(library("awsS3_core"))
    implementation(library("awsS3_s3"))
    implementation(library("awsS3_kms"))
    implementation(library("jackson_core"))
    implementation(library("jackson_annotations"))
    implementation(library("jackson_databind"))
    implementation(library("commons_httpclient"))
    implementation(library("joda"))
    implementation(library("commons_lang"))
}

testFixtures {
    from(":dependencyManagement")
    from(":ivy")
    from(":maven")
}

gradlebuildJava {
    moduleType = ModuleType.PLUGIN
}

tasks.withType<Test>().configureEach {
    val javaVersion = rootProject.the<AvailableJavaInstallations>().javaInstallationForTest.javaVersion
    if (javaVersion.isJava9Compatible) {
        jvmArgs("--add-modules", "java.xml.bind")
    }
}

testFilesCleanup {
    // TODO Improve once we have better syntax for lazy properties in Kotlin DSL
    policy.set(WhenNotEmpty.REPORT)
}
