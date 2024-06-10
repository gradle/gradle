import gradlebuild.commons.configureJavaToolChain

plugins {
    `java-library`

//    id("gradlebuild.arch-test")
//    id("gradlebuild.ci-lifecycle")
    id("gradlebuild.ci-reporting")
//    id("gradlebuild.configure-ci-artifacts") // CI: Prepare reports to be uploaded to TeamCity
    id("gradlebuild.code-quality")
//    id("gradlebuild.dependency-modules")
    id("gradlebuild.module-jar")
    id("gradlebuild.repositories")
    id("gradlebuild.reproducible-archives")
    // TODO Move this to build-logic-commons?
//    id("gradlebuild.strict-compile")
}

java {
    configureJavaToolChain()
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}
