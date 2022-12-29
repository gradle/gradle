/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

plugins {
    id("com.android.application")
    id("kotlin-android")
    id("jacoco")
}

android {
    compileSdkVersion(24)
    buildToolsVersion("$androidBuildToolsVersion")
    namespace = "org.gradle.smoketest.kotlin.android"
    defaultConfig {
        applicationId = "org.gradle.smoketest.kotlin.android"
        minSdkVersion(16)
        targetSdkVersion(24)
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "android.support.test.runner.AndroidJUnitRunner"
    }
    buildFeatures {
        viewBinding = true
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
        }
    }
    sourceSets {
        getByName("main").java.srcDir("src/main/kotlin")
        getByName("test").java.srcDir("src/test/kotlin")
        getByName("androidTest").java.srcDir("src/androidTest/kotlin")
    }
    testOptions {
        unitTests.withGroovyBuilder {
            // AGP >= 4.0.0 exposes `returnDefaultValues`
            // AGP < 4.0.0 exposes `isReturnDefaultValues
            setProperty("returnDefaultValues", true)
        }
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

project.afterEvaluate {
    // Grab all build types and product flavors
    val buildTypes = android.buildTypes.map { type ->
        type.name
    }
    // When no product flavors defined, use empty
    val productFlavors = android.productFlavors.map { flavor -> flavor.name }
        .takeIf { it.isNotEmpty() }
        ?: listOf("")
    productFlavors.forEach { productFlavorName ->
        buildTypes.forEach { buildTypeName ->
            lateinit var sourceName: String
            lateinit var sourcePath: String
            if (productFlavorName.isNotEmpty()) {
                sourceName = buildTypeName
                sourcePath = sourceName
            } else {
                sourceName = "${productFlavorName}${buildTypeName.capitalize()}"
                sourcePath = "${productFlavorName}/${buildTypeName}"
            }
            val testTaskName = "test${sourceName.capitalize()}UnitTest"
            // Create coverage task of form("testFlavorTypeCoverage" depending on("testFlavorTypeUnitTest"
            tasks.register<JacocoReport>("${testTaskName}Coverage") {
                dependsOn(testTaskName)
                group = "Reporting"
                description = "Generate Jacoco coverage reports on the ${sourceName.capitalize()} build."
                classDirectories.from(
                    fileTree("${project.buildDir}/intermediates/classes/${sourcePath}") {
                        exclude(
                            "**/R.class",
                            "**/R$*.class",
                            "**/*$" + "ViewInjector*.*",
                            "**/*$" + "ViewBinder*.*",
                            "**/BuildConfig.*",
                            "**/Manifest*.*"
                        )
                    }
                )
                val coverageSourceDirs = listOf(
                    "src/main/kotlin",
                    "src/$productFlavorName/kotlin",
                    "src/$buildTypeName/kotlin"
                )
                additionalSourceDirs.from(files(coverageSourceDirs))
                sourceDirectories.from(files(coverageSourceDirs))
                executionData.from(files("${project.buildDir}/jacoco/${testTaskName}.exec"))
                reports {
                    xml.required.set(true)
                    html.required.set(true)
                }
            }
        }
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
    testImplementation("junit:junit:4.13")
}
