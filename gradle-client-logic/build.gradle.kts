plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
}

kotlin {
    jvm()
    sourceSets {

        all {
            dependencies {
                implementation(project.dependencies.platform(libs.kotlin.bom))
                implementation(project.dependencies.platform(libs.kotlinx.coroutines.bom))
                implementation(project.dependencies.platform(libs.kotlinx.serialization.bom))
            }
        }

        jvmMain.dependencies {

            api(libs.gradle.tooling)

            implementation(libs.sqldelight.extensions.coroutines)
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.driver.sqlite)
            implementation(libs.kotlinx.serialization.core)

            implementation(libs.slf4j.api)

            runtimeOnly(libs.logback.classic)
        }

        jvmTest.dependencies {
            implementation(libs.junit.junit)
        }
    }
}

sqldelight {
    databases {
        create("ApplicationDatabase") {
            packageName = "org.gradle.client.logic.database.sqldelight.generated"
            verifyDefinitions = true
            verifyMigrations = true
            deriveSchemaFromMigrations = true
            generateAsync = false
        }
    }
}
