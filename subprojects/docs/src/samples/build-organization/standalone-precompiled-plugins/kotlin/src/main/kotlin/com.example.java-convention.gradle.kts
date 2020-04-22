plugins {
    java
    checkstyle

    // NOTE: external plugin version is specified in implementation dependency of build.gradle for precompiled script plugins
    id("com.github.spotbugs")
}

val checkstyleConfigFile = project.file("$buildDir/checkstyle.xml")

afterEvaluate {
    buildDir.mkdir()
    com.example.CheckstyleUtil.copyFileFromJar("/checkstyle.xml", checkstyleConfigFile)
}

checkstyle {
    configFile = checkstyleConfigFile
    maxWarnings = 0
}

tasks.withType<JavaCompile>() {
    options.compilerArgs.add("-Xlint:deprecation")
}

repositories {
    jcenter() // could be a company's private repository
}