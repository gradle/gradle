import gradlebuild.commons.configureJavaToolChain

plugins {
    `java-library`
    groovy

    id("gradlebuild.ci-reporting")
    id("gradlebuild.code-quality")
    id("gradlebuild.module-jar")
    id("gradlebuild.repositories")
    id("gradlebuild.reproducible-archives")
    id("gradlebuild.private-javadoc")
}

description = "A plugin that sets up a Java code that is shared between build-logic and runtime"

java {
    configureJavaToolChain()
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

testing {
    suites {
        val test by getting(JvmTestSuite::class) {
            useSpock()
        }
    }
}
