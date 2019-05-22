import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    `java-library`
    gradlebuild.`strict-compile`
    gradlebuild.classycle
}

dependencies {
    implementation(project(":baseServices"))
    implementation(project(":logging"))
    implementation(project(":resources"))
    implementation(project(":resourcesHttp"))
    implementation(project(":core"))

    implementation(library("slf4j_api"))
    implementation(library("guava"))
    implementation(library("commons_lang"))
    implementation(library("jackson_core"))
    implementation(library("jackson_annotations"))
    implementation(library("jackson_databind"))
    implementation(library("gcs"))
    implementation(library("commons_httpclient"))
    implementation(library("joda"))

    testImplementation(library("groovy"))

    integTestImplementation(project(":coreApi"))
    integTestImplementation(project(":modelCore"))
    integTestImplementation(library("commons_io"))
    integTestImplementation(testLibrary("jetty"))
}

gradlebuildJava {
    moduleType = ModuleType.CORE
}

testFixtures {
    from(":core")
    from(":dependencyManagement")
    from(":ivy")
    from(":maven")
}
