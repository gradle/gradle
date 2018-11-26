import build.*

plugins {
    `kotlin-library`
}

dependencies {
    compile(project(":test-fixtures"))
    compile("org.xmlunit:xmlunit-matchers:2.5.1")
}

tasks {
    test {
        dependsOn(":customInstallation")
        inputs.dir("$rootDir/samples")
    }
}

withParallelTests()
