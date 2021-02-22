plugins {
    scala
}
version = "1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.scala-lang:scala-library:2.11.12")
    testImplementation("org.scalatest:scalatest_2.11:3.0.0")
    testImplementation("junit:junit:4.13")
}

// tag::scala-cross-compilation[]
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(6))
    }
}
// end::scala-cross-compilation[]
