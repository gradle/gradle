import org.gradle.gradlebuild.BuildEnvironment.javaVersion
import org.gradle.gradlebuild.unittestandcompile.ModuleType
import org.gradle.gradlebuild.testing.integrationtests.cleanup.WhenNotEmpty

plugins {
    `java-library`
    id("gradlebuild.strict-compile")
    id("gradlebuild.classycle")
}

dependencies {
    api(project(":core"))
    api(project(":resources"))

    implementation(project(":resourcesHttp"))
    implementation(library("slf4j_api"))
    implementation(library("guava"))
    implementation(library("awsS3_core"), library("awsS3_s3"), library("awsS3_kms"), library("jaxb"))
    implementation(library("jackson_core"), library("jackson_annotations"), library("jackson_databind"))
    implementation(library("commons_httpclient"), library("joda"))
    implementation(library("commons_lang"))
}

testFixtures {
    from(":dependencyManagement")
    from(":ivy")
    from(":maven")
}

gradlebuildJava {
    moduleType = ModuleType.CORE
}

testFilesCleanup {
    policy.set(WhenNotEmpty.REPORT)
}
