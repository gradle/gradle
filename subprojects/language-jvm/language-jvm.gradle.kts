import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    `java-library`
    gradlebuild.`strict-compile`
    gradlebuild.classycle
}

dependencies {
    api(library("jsr305"))

    implementation(project(":baseServices"))
    implementation(project(":logging"))
    implementation(project(":processServices"))
    implementation(project(":files"))
    implementation(project(":coreApi"))
    implementation(project(":modelCore"))
    implementation(project(":core"))
    implementation(project(":workers"))
    implementation(project(":platformBase"))
    implementation(project(":platformJvm"))

    implementation(library("groovy")) // for 'Task.property(String propertyName) throws groovy.lang.MissingPropertyException'
    implementation(library("guava"))
    implementation(library("inject"))

    testImplementation(project(":native"))
    testImplementation(project(":resources"))
    testRuntimeOnly(project(":languageJava"))

    testFixturesImplementation(library("commons_lang"))
    testFixturesImplementation(project(":internalIntegTesting"))
}

gradlebuildJava {
    moduleType = ModuleType.CORE
}

testFixtures {
    from(":core")
    from(":core", "testFixtures")
    from(":launcher", "testFixtures")
}
