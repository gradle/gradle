plugins {
    id("kotlin-library")
}

dependencies {
    compile(gradleApi())

    compile(project(":provider"))
    compile(project(":tooling-builders"))

    compile(gradleTestKit())
    compile("junit:junit:4.12")
    compile("com.nhaarman:mockito-kotlin:1.5.0")
    compile("com.fasterxml.jackson.module:jackson-module-kotlin:2.9.2")
    compile("org.ow2.asm:asm-all:5.2")
}
