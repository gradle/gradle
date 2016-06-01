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

package org.gradle.nativeplatform

import groovy.transform.NotYetImplemented
import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class NativeDependentComponentsReportIntegrationTest extends AbstractIntegrationSpec {
    // TODO: This may or may not make sense (we shouldn't fail any worse than building would)
    @NotYetImplemented
    def "circular dependencies are handled gracefully"() {
        buildFile << """
apply plugin: 'cpp'
model {
    components {
        util(NativeLibrarySpec) {
            sources {
                cpp.lib library: 'lib'
            }
        }

        lib(NativeLibrarySpec) {
            sources {
                cpp.lib library: 'util'
            }
        }

        main(NativeExecutableSpec) {
            sources {
                cpp.lib library: 'lib'
            }
        }
    }
}
"""
        when:
        succeeds("dependentComponents")
        then:
        false // TODO: once this works, assert the correct output.
        // result.output.contains("Foo")
    }

    def "report renders variant binaries"() {
        buildFile << """
apply plugin: 'cpp'
model {
    flavors {
        freeware
        shareware
        shrinkware
    }

    components {
        lib(NativeLibrarySpec)

        main(NativeExecutableSpec) {
            sources {
                cpp.lib library: 'lib'
            }
        }
    }
}
"""
        when:
        succeeds("dependentComponents")
        then:
        result.output.contains("""
------------------------------------------------------------
Root project
------------------------------------------------------------

lib - Dependent components for native library 'lib'
+--- lib:freewareSharedLibrary
|    \\--- main:freewareExecutable
+--- lib:freewareStaticLibrary
+--- lib:sharewareSharedLibrary
|    \\--- main:sharewareExecutable
+--- lib:sharewareStaticLibrary
+--- lib:shrinkwareSharedLibrary
|    \\--- main:shrinkwareExecutable
\\--- lib:shrinkwareStaticLibrary

main - Dependent components for native executable 'main'
+--- main:freewareExecutable
+--- main:sharewareExecutable
\\--- main:shrinkwareExecutable
""")
    }
}
