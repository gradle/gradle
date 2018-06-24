package org.gradle.gradlebuild.test.integrationtests

import org.gradle.api.JavaVersion.VERSION_1_10
import org.gradle.api.JavaVersion.VERSION_1_9


internal
val excludedTests = listOf(
    // Caused by: java.lang.IncompatibleClassChangeError: Method Person.getName()Ljava/lang/String; must be InterfaceMethodref constant
    // Fail since build 125
    "InterfaceBackedManagedTypeIntegrationTest" to listOf(VERSION_1_9, VERSION_1_10),

    // Test compiles for Java 5
    "ToolingApiUnsupportedClientJvmCrossVersionSpec" to listOf(VERSION_1_9, VERSION_1_10)
)
