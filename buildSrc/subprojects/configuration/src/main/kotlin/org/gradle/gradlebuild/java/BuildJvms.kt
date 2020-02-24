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

package org.gradle.gradlebuild.java

import org.gradle.api.JavaVersion
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.jvm.toolchain.JavaInstallationRegistry


const val productionJdkName = "AdoptOpenJDK 11"


/**
 * Provides access to the different JVMs that are used to build, compile and test.
 */
open class BuildJvms(
    javaInstallationRegistry: JavaInstallationRegistry,
    testJavaHome: Provider<Directory>
) {
    val buildJvm = javaInstallationRegistry.installationForCurrentVirtualMachine.map { JavaInstallation(true, it) }

    val compileJvm = buildJvm.map {
        if (it.javaVersion < JavaVersion.VERSION_1_9 || it.javaVersion > JavaVersion.VERSION_11) {
            throw IllegalStateException("Must use JDK >= 9 and <= 11 to perform compilation in this build. It's currently ${it.vendorAndMajorVersion} at ${it.javaHome}.")
        }
        it
    }

    val testJvm = javaInstallationRegistry.installationForDirectory(testJavaHome).map { JavaInstallation(false, it) }.orElse(buildJvm)

    fun validateForProductionEnvironment() {
        val buildInstallation = buildJvm.get()
        if (buildInstallation.vendorAndMajorVersion != productionJdkName) {
            throw IllegalStateException("Must use $productionJdkName to perform this build. Is currently ${buildInstallation.vendorAndMajorVersion} at ${buildInstallation.javaHome}.")
        }
    }
}
