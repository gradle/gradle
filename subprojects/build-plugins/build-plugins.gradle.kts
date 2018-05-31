import build.futureKotlin

plugins {
    id("public-kotlin-dsl-module")
}

base {
    archivesBaseName = "gradle-kotlin-dsl-build-plugins"
}


val processResources: ProcessResources by tasks
val writeKotlinDslProviderVersion by tasks.creating(WriteProperties::class) {
    outputFile = processResources.destinationDir.resolve("${base.archivesBaseName}-versions.properties")
    property("kotlin-dsl", version)
}
processResources.dependsOn(writeKotlinDslProviderVersion)


val writeTestKitPluginClasspath by tasks.creating {
    val main by java.sourceSets
    val outputDir = file("$buildDir/$name")
    val testResources = file("src/test/resources")
    inputs.files(main.runtimeClasspath)
    inputs.dir(testResources)
    outputs.dir(outputDir)
    doLast {
        outputDir.mkdirs()
        file("$outputDir/plugin-classpath.txt").writeText(main.runtimeClasspath.plus(testResources).joinToString("\n"))
    }
}


dependencies {

    compileOnly(gradleApi())
    compileOnly(project(":provider"))

    implementation("com.thoughtworks.qdox:qdox:2.0-M8")
    implementation(futureKotlin("gradle-plugin"))

    testImplementation(project(":provider"))
    testImplementation(project(":test-fixtures"))
    testImplementation(files(writeTestKitPluginClasspath))
}


val customInstallation by rootProject.tasks
tasks {
    "test" {
        dependsOn(customInstallation)
    }
}
