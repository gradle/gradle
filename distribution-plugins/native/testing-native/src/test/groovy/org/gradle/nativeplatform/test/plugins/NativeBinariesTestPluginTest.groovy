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

package org.gradle.nativeplatform.test.plugins

import org.gradle.nativeplatform.StaticLibraryBinarySpec
import org.gradle.nativeplatform.platform.NativePlatform
import org.gradle.nativeplatform.platform.OperatingSystem
import org.gradle.nativeplatform.tasks.InstallExecutable
import org.gradle.nativeplatform.test.NativeTestSuiteBinarySpec
import org.gradle.nativeplatform.test.tasks.RunTestExecutable
import org.gradle.platform.base.PlatformBaseSpecification

class NativeBinariesTestPluginTest extends PlatformBaseSpecification {
    def "registers NativeTestSuiteBinarySpec"() {
        when:
        dsl {
            apply plugin: NativeBinariesTestPlugin
            model {
                library(StaticLibraryBinarySpec)
            }
        }

        def library = realize("library")

        dsl {
            model {
                binary(NativeTestSuiteBinarySpec) {
                    testedBinary = library
                    tasks.create("run", RunTestExecutable) {}
                    tasks.create("install", InstallExecutable) {
                        it.installDirectory = new File(".")
                        it.executableFile = new File("exe")
                        it.targetPlatform = Mock(NativePlatform) {
                            getOperatingSystem() >> Mock(OperatingSystem) {
                                getName() >> "test"
                            }
                        }
                    }
                }
            }
        }

        then:
        realize("binary") instanceof NativeTestSuiteBinarySpec
    }
}
