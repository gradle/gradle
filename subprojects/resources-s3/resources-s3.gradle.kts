import org.gradle.gradlebuild.unittestandcompile.ModuleType
import org.gradle.gradlebuild.testing.integrationtests.cleanup.WhenNotEmpty

plugins {
    `java-library`
    gradlebuild.`strict-compile`
    gradlebuild.classycle
}

dependencies {
    implementation(project(":baseServices"))
    implementation(project(":coreApi"))
    implementation(project(":core"))
    implementation(project(":resources"))
    implementation(project(":resourcesHttp"))
    
    implementation(library("slf4j_api"))
    implementation(library("guava"))
    implementation(library("nativePlatform"))
    implementation(library("awsS3_core"))
    implementation(library("awsS3_s3"))
    implementation(library("awsS3_kms"))
    implementation(library("jaxb"))
    implementation(library("jackson_core"))
    implementation(library("jackson_annotations"))
    implementation(library("jackson_databind"))
    implementation(library("commons_httpclient"))
    implementation(library("joda"))
    implementation(library("commons_lang"))

    integTestImplementation(project(":logging"))
    integTestImplementation(library("commons_io"))
    integTestImplementation(testLibrary("littleproxy"))
    integTestImplementation(testLibrary("jetty"))
}

testFixtures {
    from(":core")
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
