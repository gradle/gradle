plugins {
    groovy
}

dependencies {
    implementation(localGroovy())
    implementation(gradleApi())
    testImplementation(gradleTestKit())
    testImplementation("org.spockframework:spock-core:1.1-groovy-2.4") {
        exclude(module = "groovy-all")
    }
}

repositories {
    mavenCentral()
}

// tag::test-logic-classpath[]
// Write the plugin's classpath to a file to share with the tests
tasks.register("createClasspathManifest") {
    val outputDir = file("$buildDir/$name")

    inputs.files(sourceSets.main.get().runtimeClasspath)
    outputs.dir(outputDir)

    doLast {
        outputDir.mkdirs()
        file("$outputDir/plugin-classpath.txt").writeText(sourceSets.main.get().runtimeClasspath.joinToString("\n"))
    }
}

// Add the classpath file to the test runtime classpath
dependencies {
    testRuntimeOnly(files(tasks["createClasspathManifest"]))
}
// end::test-logic-classpath[]
