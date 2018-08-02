import build.*

plugins {
    id("kotlin-library")
}

dependencies {
    compile(project(":test-fixtures"))
    compile("org.xmlunit:xmlunit-matchers:2.5.1")
}

tasks.named("test").configure {
    dependsOn(rootProject.tasks.named("customInstallation"))
    inputs.dir("$rootDir/samples")
}

withParallelTests()
