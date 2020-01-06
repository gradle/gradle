import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    `java-library`
    gradlebuild.`strict-compile`
}

dependencies {
    implementation(project(":baseServices"))
    implementation(project(":logging"))
    implementation(project(":coreApi"))
    implementation(project(":files"))
    implementation(project(":modelCore"))
    implementation(project(":core"))
    implementation(project(":dependencyManagement"))
    implementation(project(":workers"))
    implementation(project(":execution"))

    implementation(library("groovy"))
    implementation(library("guava"))
    implementation(library("commons_lang"))

    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":coreApi")))
    testImplementation(project(":native"))
    testImplementation(project(":snapshots"))
    testImplementation(project(":processServices"))

    testRuntimeOnly(project(":runtimeApiInfo"))

    testFixturesApi(project(":core"))
    testFixturesApi(project(":fileCollections"))
    testFixturesApi(testFixtures(project(":modelCore")))
    testFixturesImplementation(library("guava"))
    testFixturesApi(testFixtures(project(":modelCore")))
    testFixturesApi(testFixtures(project(":diagnostics")))
}

gradlebuildJava {
    moduleType = ModuleType.CORE
}
