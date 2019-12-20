import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    `java-library`
}

configurations {
    create("reports")
}

repositories {
    googleApisJs()
}

dependencies {
    implementation(project(":baseServices"))
    implementation(project(":logging"))
    implementation(project(":fileCollections"))
    implementation(project(":coreApi"))
    implementation(project(":modelCore"))
    implementation(project(":core"))

    implementation(library("groovy"))
    implementation(library("guava"))
    implementation(library("inject"))
    implementation(library("jatl"))

    testImplementation(project(":processServices"))
    testImplementation(project(":baseServicesGroovy"))
    testImplementation(testLibrary("jsoup"))
    testImplementation(testFixtures(project(":core")))

    testRuntimeOnly(project(":runtimeApiInfo"))
    testRuntimeOnly(project(":workers"))
    testRuntimeOnly(project(":dependencyManagement"))

    integTestRuntimeOnly(project(":codeQuality"))
    integTestRuntimeOnly(project(":jacoco"))

    integTestRuntimeOnly(project(":testingJunitPlatform"))

    add("reports", "jquery:jquery.min:1.11.0@js")
}

gradlebuildJava {
    moduleType = ModuleType.CORE
}

val generatedResourcesDir = gradlebuildJava.generatedResourcesDir

val reportResources by tasks.registering(Copy::class) {
    from(configurations["reports"])
    into("$generatedResourcesDir/org/gradle/reporting")
}
sourceSets.main {
    output.dir(generatedResourcesDir, "builtBy" to reportResources)
}
