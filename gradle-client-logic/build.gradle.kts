plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    jvm()
    sourceSets {

        jvmMain.dependencies {

            implementation(libs.gradle.tooling)

            runtimeOnly(libs.slf4j.simple)
        }

        jvmTest.dependencies {
            implementation(libs.junit.jupiter)
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
