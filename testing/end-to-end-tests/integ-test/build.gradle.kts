plugins {
    id("gradlebuild.internal.java")
}

dependencies {
    integTestImplementation("org.gradle:base-services")
    integTestImplementation("org.gradle:native")
    integTestImplementation("org.gradle:logging")
    integTestImplementation("org.gradle:process-services")
    integTestImplementation("org.gradle:core-api")
    integTestImplementation("org.gradle:resources")
    integTestImplementation("org.gradle:persistent-cache")
    integTestImplementation("org.gradle:bootstrap")
    integTestImplementation("org.gradle:launcher")
    integTestImplementation("org.gradle:dependency-management")
    integTestImplementation(libs.groovy)
    integTestImplementation(libs.slf4jApi)
    integTestImplementation(libs.guava)
    integTestImplementation(libs.ant)
    integTestImplementation(libs.jsoup)

    integTestImplementation(libs.sampleCheck) {
        exclude(group = "org.codehaus.groovy", module = "groovy-all")
        exclude(module = "slf4j-simple")
    }
    integTestImplementation(testFixtures(project(":model-core")))

    crossVersionTestImplementation("org.gradle:base-services")
    crossVersionTestImplementation("org.gradle:core")
    crossVersionTestImplementation("org.gradle:plugins")
    crossVersionTestImplementation("org.gradle:platform-jvm")
    crossVersionTestImplementation("org.gradle:language-java")
    crossVersionTestImplementation("org.gradle:language-groovy")
    crossVersionTestImplementation("org.gradle:scala")
    crossVersionTestImplementation("org.gradle:ear")
    crossVersionTestImplementation("org.gradle:testing-jvm")
    crossVersionTestImplementation("org.gradle:ide")
    crossVersionTestImplementation("org.gradle:code-quality")
    crossVersionTestImplementation("org.gradle:signing")

    integTestImplementation(testFixtures("org.gradle:core"))
    integTestImplementation(testFixtures("org.gradle:diagnostics"))
    integTestImplementation(testFixtures("org.gradle:platform-native"))
    integTestImplementation(libs.jgit)
    integTestImplementation(libs.javaParser) {
        because("The Groovy compiler inspects the dependencies at compile time")
    }

    integTestDistributionRuntimeOnly("org.gradle:distributions-full")
    crossVersionTestDistributionRuntimeOnly("org.gradle:distributions-full")
}

testFilesCleanup.reportOnly.set(true)
