/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.smoketests

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class KotlinPluginSmokeSpec extends AbstractSmokeSpec {

    def 'kotlin plugin'() {
        given:
        def kotlinVersion = '1.0.2'
        buildFile << """
            buildscript {
                ext.kotlin_version = '$kotlinVersion'

                repositories {
                    mavenCentral()
                }

                dependencies {
                    classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion"
                }
            }

            apply plugin: 'kotlin'

            repositories {
                mavenCentral()
            }

            dependencies {
                compile "org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion"
            }
        """.stripIndent()

        file('src/main/kotlin/pkg/HelloWorld.kt') << """
        package pkg

        fun getGreeting(): String {
            val words = mutableListOf<String>()
            words.add("Hello,")
            words.add("world!")

            return words.joinToString(separator = " ")
        }

        fun main(args: Array<String>) {
            println(getGreeting())
        }
        """.stripIndent()

        when:
        def result = runner('build').build()

        then:
        result.task(':compileKotlin').outcome == SUCCESS
    }

    def 'kotlin android plugin'() {

        given:
        buildFile << """
buildscript {
    repositories {
        jcenter()
    }
    dependencies {
        classpath 'com.android.tools.build:gradle:2.2.0-alpha6'
        classpath 'org.jetbrains.kotlin:kotlin-gradle-plugin:1.0.3'
        classpath 'org.jetbrains.kotlin:kotlin-android-extensions:1.0.3'
    }
}

System.properties['com.android.build.gradle.overrideVersionCheck'] = 'true'

repositories {
    jcenter()
}

apply plugin: 'com.android.application'
apply plugin: 'kotlin-android'
apply plugin: 'kotlin-android-extensions'

android {
    compileSdkVersion 24
    buildToolsVersion "24.0.0"
    defaultConfig {
        applicationId "org.gradle.smoketest.kotlin.android"
        minSdkVersion 16
        targetSdkVersion 24
        versionCode 1
        versionName "1.0"
        testInstrumentationRunner "android.support.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.pro'
        }
    }
    sourceSets {
        main.java.srcDirs += 'src/main/kotlin'
        test.java.srcDirs += 'src/test/kotlin'
        androidTest.java.srcDirs += 'src/androidTest/kotlin'
    }
    testOptions {
        unitTests.returnDefaultValues = true
    }
}

apply plugin: 'jacoco'

project.afterEvaluate {
    // Grab all build types and product flavors
    def buildTypes = android.buildTypes.collect { type ->
        type.name
    }
    def productFlavors = android.productFlavors.collect { flavor ->
        flavor.name
    }
    // When no product flavors defined, use empty
    if (!productFlavors) productFlavors.add('')
    productFlavors.each { productFlavorName ->
        buildTypes.each { buildTypeName ->
            def sourceName, sourcePath
            if (!productFlavorName) {
                sourceName = sourcePath = "\${buildTypeName}"
            } else {
                sourceName = "\${productFlavorName}\${buildTypeName.capitalize()}"
                sourcePath = "\${productFlavorName}/\${buildTypeName}"
            }
            def testTaskName = "test\${sourceName.capitalize()}UnitTest"
            // Create coverage task of form 'testFlavorTypeCoverage' depending on 'testFlavorTypeUnitTest'
            task "\${testTaskName}Coverage" (type:JacocoReport, dependsOn: "\$testTaskName") {
                group = "Reporting"
                description = "Generate Jacoco coverage reports on the \${sourceName.capitalize()} build."
                classDirectories = fileTree(
                        dir: "\${project.buildDir}/intermediates/classes/\${sourcePath}",
                        excludes: [
                                '**/R.class',
                                '**/R\$*.class',
                                '**/*\$ViewInjector*.*',
                                '**/*\$ViewBinder*.*',
                                '**/BuildConfig.*',
                                '**/Manifest*.*'
                        ]
                )
                def coverageSourceDirs = [
                        "src/main/kotlin",
                        "src/\$productFlavorName/kotlin",
                        "src/\$buildTypeName/kotlin"
                ]
                additionalSourceDirs = files(coverageSourceDirs)
                sourceDirectories = files(coverageSourceDirs)
                executionData = files("\${project.buildDir}/jacoco/\${testTaskName}.exec")
                reports {
                    xml.enabled = true
                    html.enabled = true
                }
            }
        }
    }
}

dependencies {
    compile 'org.jetbrains.kotlin:kotlin-stdlib:1.0.3'
    compile 'org.jetbrains.anko:anko-common:0.9'

    testCompile 'junit:junit:4.12'
}
""".stripIndent()

        file("src/main/AndroidManifest.xml") << """<manifest package="me.egorand.jacocokotlinissue"
          xmlns:android="http://schemas.android.com/apk/res/android">

    <application
        android:allowBackup="true"
        android:supportsRtl="true">
    </application>
</manifest>"""

        file("src/main/kotlin/org/gradle/smoketest/kotlin/android/StringPrinterFragment.kt") << """
package org.gradle.smoketest.kotlin.android

import android.app.Fragment
import android.util.Log
import org.jetbrains.anko.runOnUiThread

class StringPrinterFragment : Fragment() {

    fun printStringLength(str: String) = runOnUiThread {
        Log.d("StringPrinter", str.length.toString())
    }
}"""

        file("src/test/kotlin/org/gradle/smoketest/kotlin/android/StringPrinterTest.kt") << """
package org.gradle.smoketest.kotlin.android

import org.junit.Before
import org.junit.Test

class StringPrinterTest {

    lateinit private var stringPrinter: StringPrinterFragment

    @Before fun setUp() {
        stringPrinter = StringPrinterFragment()
    }

    @Test fun shouldPrintStringLength() {
        stringPrinter.printStringLength("Hello world!")
    }
}"""

        when:
        def build = runner('clean', 'testDebugUnitTestCoverage').build()

        then:
        build.task(':testDebugUnitTestCoverage').outcome == SUCCESS
    }

}
