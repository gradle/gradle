import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    `java-library`
    gradlebuild.classycle
}

dependencies {
    implementation(project(":baseServices"))
    implementation(project(":logging"))
    implementation(project(":fileCollections"))
    implementation(project(":execution"))
    implementation(project(":processServices"))
    implementation(project(":coreApi"))
    implementation(project(":modelCore"))
    implementation(project(":core"))
    implementation(project(":baseServicesGroovy"))
    implementation(project(":dependencyManagement"))
    implementation(project(":platformBase"))
    implementation(project(":diagnostics"))

    implementation(library("groovy"))
    implementation(library("guava"))
    implementation(library("commons_lang"))
    implementation(library("commons_io"))
    implementation(library("inject"))
    implementation(library("asm"))

    testImplementation(project(":native"))
    testImplementation(library("ant"))
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":diagnostics")))
    testImplementation(testFixtures(project(":platformBase")))
    testImplementation(testFixtures(project(":platformNative")))

    testRuntimeOnly(project(":runtimeApiInfo"))

    integTestImplementation(library("slf4j_api"))
}

gradlebuildJava {
    moduleType = ModuleType.CORE
}

