plugins {
    gradlebuild.distribution.`api-java`
}

dependencies {
    implementation(project(":baseServices"))
    implementation(project(":files"))
    implementation(project(":logging"))
    implementation(project(":processServices"))
    implementation(project(":workerProcesses"))
    implementation(project(":persistentCache"))
    implementation(project(":coreApi"))
    implementation(project(":modelCore"))
    implementation(project(":core"))
    implementation(project(":workers"))
    implementation(project(":platformBase"))
    implementation(project(":platformJvm"))
    implementation(project(":languageJava"))
    implementation(project(":languageJvm"))

    implementation(library("groovy")) // for 'Task.property(String propertyName) throws groovy.lang.MissingPropertyException'
    implementation(library("ant"))
    implementation(library("slf4j_api"))
    implementation(library("guava"))
    implementation(library("inject"))

    testImplementation(project(":fileCollections"))
    testImplementation(project(":files"))
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":platformBase")))
    testImplementation(testFixtures(project(":plugins")))

    integTestImplementation(library("commons_lang"))
    integTestImplementation(library("ant"))

    testFixturesApi(testFixtures(project(":languageJvm")))
    testFixturesImplementation(project(":baseServices"))
    testFixturesImplementation(project(":coreApi"))
    testFixturesImplementation(project(":modelCore"))
    testFixturesImplementation(project(":platformBase"))
    testFixturesImplementation(testFixtures(project(":languageJvm")))

    compileOnly("org.scala-sbt:zinc_2.12:1.3.5")

    testRuntimeOnly(project(":distributionsJvm")) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly(project(":distributionsJvm"))
}

strictCompile {
    ignoreDeprecations() // uses deprecated software model types
}

classycle {
    excludePatterns.set(listOf(
        "org/gradle/api/internal/tasks/scala/**",
        "org/gradle/language/scala/internal/toolchain/**"))
}

