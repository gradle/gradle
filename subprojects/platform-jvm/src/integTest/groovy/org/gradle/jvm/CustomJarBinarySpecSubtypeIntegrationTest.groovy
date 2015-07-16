/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.jvm
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.archive.JarTestFixture

class CustomJarBinarySpecSubtypeIntegrationTest extends AbstractIntegrationSpec {
    def "can create a Jar from a custom managed JarBinarySpec subtype"() {
        buildFile << """
plugins {
    id 'jvm-component'
}

@Managed
interface CustomJarBinarySpec extends JarBinarySpec {
    String getValue()
    void setValue(String value)
}

import org.gradle.jvm.platform.internal.DefaultJavaPlatform

class CustomJarBinarySpecRules extends RuleSource {
    @BinaryType
    void customJarBinary(BinaryTypeBuilder<CustomJarBinarySpec> builder) {
    }

    @Finalize
    void setToolChainsForBinaries(ModelMap<BinarySpec> binaries) {
        def platform = DefaultJavaPlatform.current()
        binaries.withType(CustomJarBinarySpec).beforeEach { binary ->
            binary.targetPlatform = platform
        }
    }
}

apply plugin: CustomJarBinarySpecRules

model {
    components {
        sampleLib(JvmLibrarySpec) {
            binaries {
                customJar(CustomJarBinarySpec) {
                    value = "12"
                }
            }
        }
    }
}
"""
        expect:
        succeeds "components", "customJar"
        new JarTestFixture(file("build/jars/customJar/sampleLib.jar")).isManifestPresentAndFirstEntry()
    }
}
