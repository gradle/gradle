import accessors.javaScript
import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    `java-library`
    `javascript-base`
}

configurations {
    create("reports")
}

repositories {
    javaScript.googleApis()
}

dependencies {
    implementation(project(":baseServices"))
    implementation(project(":logging"))
    implementation(project(":files"))
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

    integTestRuntimeOnly(project(":codeQuality"))
    integTestRuntimeOnly(project(":jacoco"))

    add("reports", "jquery:jquery.min:1.11.0@js")
}

gradlebuildJava {
    moduleType = ModuleType.CORE
}

testFixtures {
    from(":core")
}

val generatedResourcesDir = gradlebuildJava.generatedResourcesDir

val reportResources by tasks.registering(Copy::class) {
    from(configurations["reports"])
    into("$generatedResourcesDir/org/gradle/reporting")
}
sourceSets.main {
    output.dir(generatedResourcesDir, "builtBy" to reportResources)
}
