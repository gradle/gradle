plugins {
    java
}

version.set("1.0.2")
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
