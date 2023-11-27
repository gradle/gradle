plugins {
    java
}

version = "1.0.2"
group = "org.gradle.sample"

repositories {
    mavenCentral()
}

testing {
    suites {
        val test by getting(JvmTestSuite::class) {
            useJUnitJupiter()
        }
    }
}
