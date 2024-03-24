plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    jvm()
    sourceSets {

        jvmMain.dependencies {

            implementation(libs.gradle.tooling)
            implementation(libs.slf4j.api)
            implementation(libs.logback.classic)
        }

        jvmTest.dependencies {
            implementation(libs.junit.jupiter)
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
