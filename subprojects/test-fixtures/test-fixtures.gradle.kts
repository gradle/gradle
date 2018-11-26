import build.*

plugins {
    `kotlin-library`
}

dependencies {
    compile(gradleApiWithParameterNames())

    compile(project(":provider"))
    compile(project(":tooling-builders"))

    compile(gradleTestKit())
    compile("junit:junit:4.12")
    compile("com.nhaarman:mockito-kotlin:1.6.0")
    compile("com.fasterxml.jackson.module:jackson-module-kotlin:2.9.2")
    compile("org.ow2.asm:asm:6.2.1")
}
