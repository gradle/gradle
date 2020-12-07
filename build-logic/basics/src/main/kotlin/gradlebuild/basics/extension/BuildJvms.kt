/*
 * Copyright 2020 the original author or authors.
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

package gradlebuild.basics.extension

import org.gradle.api.JavaVersion
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.jvm.toolchain.JavaInstallation
import org.gradle.jvm.toolchain.JavaInstallationRegistry


/**
 * Provides access to the different JVMs that are used to build, compile and test.
 */
abstract class BuildJvms(javaInstallationRegistry: JavaInstallationRegistry, testJavaHome: Provider<Directory>) {
    private
    val productionJdkName = "AdoptOpenJDK 11"

    val buildJvm = javaInstallationRegistry.installationForCurrentVirtualMachine

    val compileJvm = buildJvm.map {
        if (it.javaVersion < JavaVersion.VERSION_1_9 || it.javaVersion > JavaVersion.VERSION_11) {
            throw IllegalStateException("Must use JDK >= 9 and <= 11 to perform compilation in this build. It's currently ${it.vendorAndMajorVersion()} at ${it.installationDirectory}.")
        }
        it
    }

    private
    val testJvm = javaInstallationRegistry.installationForDirectory(testJavaHome).orElse(buildJvm)

    fun validateForProductionEnvironment() {
        val buildInstallation = buildJvm.get()
        if (buildInstallation.vendorAndMajorVersion() != productionJdkName) {
            throw IllegalStateException("Must use $productionJdkName to perform this build. Is currently ${buildInstallation.vendorAndMajorVersion()} at ${buildInstallation.installationDirectory}.")
        }
    }

    fun whenTestingWithEarlierThan(version: JavaVersion, action: (jvm: JavaInstallation) -> Unit) {
        if (testJvm.map { it.javaVersion < version }.get()) {
            action(testJvm.get())
        }
    }
}


fun JavaInstallation.vendorAndMajorVersion() = "$implementationName $javaVersion"
