import accessors.javaScript
import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    `javascript-base`
}

configurations {
    create("reports")
}

repositories {
    javaScript.googleApis()
}

dependencies {
    compile(library("groovy"))
    compile(project(":core"))
    compile(library("jatl"))

    testCompile(testLibrary("jsoup"))
    integTestRuntime(project(":codeQuality"))
    integTestRuntime(project(":jacoco"))

    add("reports", "jquery:jquery.min:1.11.0@js")
}

gradlebuildJava {
    moduleType = ModuleType.WORKER
}

testFixtures {
    from(":core")
}

val generatedResourcesDir = gradlebuildJava.generatedResourcesDir

val reportResources by tasks.registering(Copy::class) {
    from(configurations.getByName("reports"))
    into("$generatedResourcesDir/org/gradle/reporting")
}
sourceSets.main {
    output.dir(generatedResourcesDir, "builtBy" to reportResources)
}
