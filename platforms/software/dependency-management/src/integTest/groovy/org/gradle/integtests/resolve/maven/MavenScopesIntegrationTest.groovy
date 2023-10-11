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

package org.gradle.integtests.resolve.maven

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture

class MavenScopesIntegrationTest extends AbstractDependencyResolutionTest {
    def resolve = new ResolveTestFixture(buildFile, "conf")

    def setup() {
        resolve.prepare()
        resolve.addDefaultVariantDerivationStrategy()
        settingsFile << """
            rootProject.name = 'testproject'
        """
        buildFile << """
            repositories {
                maven { url '${mavenRepo.uri}' }
            }
            configurations {
                conf
            }
"""
    }

    def "prefers the runtime variant of a Maven module"() {
        def notRequired = mavenRepo.module('test', 'dont-include-me', '1.0')
        def m1 = mavenRepo.module('test', 'test1', '1.0').publish()
        def m2 = mavenRepo.module('test', 'test2', '1.0').publish()
        def m3 = mavenRepo.module('test', 'test3', '1.0').publish()
        def m4 = mavenRepo.module('test', 'test4', '1.0').publish()
        def m5 = mavenRepo.module('test', 'test5', '1.0')
            .dependsOn(m1, scope: 'compile')
            .dependsOn(m2, scope: 'runtime')
            .dependsOn(notRequired, scope: 'test')
            .dependsOn(notRequired, scope: 'provided')
            .publish()
        def m6 = mavenRepo.module('test', 'test6', '1.0')
            .dependsOn(m3, scope: 'compile')
            .dependsOn(m4, scope: 'runtime')
            .dependsOn(notRequired, scope: 'test')
            .dependsOn(notRequired, scope: 'provided')
            .publish()
        mavenRepo.module('test', 'target', '1.0')
            .dependsOn(m5, scope: 'compile')
            .dependsOn(m6, scope: 'runtime')
            .dependsOn(notRequired, scope: 'test')
            .dependsOn(notRequired, scope: 'provided')
            .publish()

        buildFile << """
dependencies {
    conf 'test:target:1.0'
}
"""
        expect:
        succeeds 'checkDep'
        resolve.expectDefaultConfiguration("runtime")
        resolve.expectGraph {
            root(':', ':testproject:') {
                module('test:target:1.0') {
                    module('test:test5:1.0') {
                        module('test:test1:1.0')
                        module('test:test2:1.0')
                    }
                    module('test:test6:1.0') {
                        module('test:test3:1.0')
                        module('test:test4:1.0')
                    }
                }
            }
        }
    }

    def "can reference compile scope to include compile scoped dependencies of module"() {
        def notRequired = mavenRepo.module('test', 'dont-include-me', '1.0')
        def m1 = mavenRepo.module('test', 'test1', '1.0').publish()
        def m2 = mavenRepo.module('test', 'test2', '1.0')
            .dependsOn(m1, scope: 'compile')
            .dependsOn(notRequired, scope: 'runtime')
            .dependsOn(notRequired, scope: 'test')
            .dependsOn(notRequired, scope: 'provided')
            .publish()
        mavenRepo.module('test', 'target', '1.0')
            .dependsOn(m2, scope: 'compile')
            .dependsOn(notRequired, scope: 'runtime')
            .dependsOn(notRequired, scope: 'test')
            .dependsOn(notRequired, scope: 'provided')
            .publish()

        buildFile << """
dependencies {
    conf group: 'test', name: 'target', version: '1.0', configuration: 'compile'
}
"""

        expect:
        succeeds 'checkDep'
        resolve.expectGraph {
            root(':', ':testproject:') {
                module('test:target:1.0') {
                    configuration = 'compile'
                    module('test:test2:1.0') {
                        module('test:test1:1.0')
                    }
                }
            }
        }
    }

    def "can reference runtime scope to include runtime dependencies of compile and runtime scoped dependencies of module"() {
        def notRequired = mavenRepo.module('test', 'dont-include-me', '1.0')
        def m1 = mavenRepo.module('test', 'test1', '1.0').publish()
        def m2 = mavenRepo.module('test', 'test2', '1.0').publish()
        def m3 = mavenRepo.module('test', 'test3', '1.0').publish()
        def m4 = mavenRepo.module('test', 'test4', '1.0').publish()
        def m5 = mavenRepo.module('test', 'test5', '1.0')
            .dependsOn(m1, scope: 'compile')
            .dependsOn(m2, scope: 'runtime')
            .dependsOn(notRequired, scope: 'test')
            .dependsOn(notRequired, scope: 'provided')
            .publish()
        def m6 = mavenRepo.module('test', 'test6', '1.0')
            .dependsOn(m3, scope: 'compile')
            .dependsOn(m4, scope: 'runtime')
            .dependsOn(notRequired, scope: 'test')
            .dependsOn(notRequired, scope: 'provided')
            .publish()
        mavenRepo.module('test', 'target', '1.0')
            .dependsOn(m5, scope: 'compile')
            .dependsOn(m6, scope: 'runtime')
            .dependsOn(notRequired, scope: 'test')
            .dependsOn(notRequired, scope: 'provided')
            .publish()

        buildFile << """
dependencies {
    conf group: 'test', name: 'target', version: '1.0', configuration: 'runtime'
}
"""
        expect:
        succeeds 'checkDep'
        resolve.expectGraph {
            root(':', ':testproject:') {
                module('test:target:1.0') {
                    configuration = 'runtime'
                    module('test:test5:1.0') {
                        module('test:test1:1.0')
                        module('test:test2:1.0')
                    }
                    module('test:test6:1.0') {
                        module('test:test3:1.0')
                        module('test:test4:1.0')
                    }
                }
            }
        }
    }

    def "can reference provided scope to include runtime dependencies of provided scoped dependencies of module"() {
        def notRequired = mavenRepo.module('test', 'dont-include-me', '1.0')
        def m1 = mavenRepo.module('test', 'test1', '1.0').publish()
        def m2 = mavenRepo.module('test', 'test2', '1.0').publish()
        def m3 = mavenRepo.module('test', 'test3', '1.0').publish()
        def m4 = mavenRepo.module('test', 'test4', '1.0')
            .dependsOn(m1, scope: 'compile')
            .dependsOn(m2, scope: 'runtime')
            .dependsOn(notRequired, scope: 'test')
            .dependsOn(notRequired, scope: 'provided')
            .publish()
        def m5 = mavenRepo.module('test', 'test5', '1.0')
            .dependsOn(m3, scope: 'compile')
            .dependsOn(m4, scope: 'runtime')
            .dependsOn(notRequired, scope: 'test')
            .dependsOn(notRequired, scope: 'provided')
            .publish()
        mavenRepo.module('test', 'target', '1.0')
            .dependsOn(notRequired, scope: 'compile')
            .dependsOn(notRequired, scope: 'runtime')
            .dependsOn(notRequired, scope: 'test')
            .dependsOn(m5, scope: 'provided')
            .publish()

        buildFile << """
dependencies {
    conf group: 'test', name: 'target', version: '1.0', configuration: 'provided'
}
"""
        expect:
        succeeds 'checkDep'
        resolve.expectGraph {
            root(':', ':testproject:') {
                module('test:target:1.0') {
                    configuration = 'provided'
                    module('test:test5:1.0') {
                        module('test:test3:1.0')
                        module('test:test4:1.0') {
                            module('test:test2:1.0')
                            module('test:test1:1.0')
                        }
                    }
                }
            }
        }
    }

    def "can reference test scope to include test dependencies of compile, runtime and test scoped dependencies of module"() {
        def notRequired = mavenRepo.module('test', 'dont-include-me', '1.0')
        def m1 = mavenRepo.module('test', 'test1', '1.0').publish()
        def m2 = mavenRepo.module('test', 'test2', '1.0').publish()
        def m3 = mavenRepo.module('test', 'test3', '1.0').publish()
        def m4 = mavenRepo.module('test', 'test4', '1.0').publish()
        def m5 = mavenRepo.module('test', 'test5', '1.0').publish()
        def m6 = mavenRepo.module('test', 'test6', '1.0').publish()
        def m7 = mavenRepo.module('test', 'test7', '1.0')
            .dependsOn(m1, scope: 'compile')
            .dependsOn(m2, scope: 'runtime')
            .dependsOn(notRequired, scope: 'test')
            .dependsOn(notRequired, scope: 'provided')
            .publish()
        def m8 = mavenRepo.module('test', 'test8', '1.0')
            .dependsOn(m3, scope: 'compile')
            .dependsOn(m4, scope: 'runtime')
            .dependsOn(notRequired, scope: 'test')
            .dependsOn(notRequired, scope: 'provided')
            .publish()
        def m9 = mavenRepo.module('test', 'test9', '1.0')
            .dependsOn(m5, scope: 'compile')
            .dependsOn(m6, scope: 'runtime')
            .dependsOn(notRequired, scope: 'test')
            .dependsOn(notRequired, scope: 'provided')
            .publish()
        mavenRepo.module('test', 'target', '1.0')
            .dependsOn(m7, scope: 'compile')
            .dependsOn(m8, scope: 'runtime')
            .dependsOn(m9, scope: 'test')
            .dependsOn(notRequired, scope: 'provided')
            .publish()

        buildFile << """
dependencies {
    conf group: 'test', name: 'target', version: '1.0', configuration: 'test'
}
"""
        expect:
        succeeds 'checkDep'
        resolve.expectGraph {
            root(':', ':testproject:') {
                module('test:target:1.0') {
                    configuration = 'test'
                    module('test:test7:1.0') {
                        module('test:test1:1.0')
                        module('test:test2:1.0')
                    }
                    module('test:test8:1.0') {
                        module('test:test3:1.0')
                        module('test:test4:1.0')
                    }
                    module('test:test9:1.0') {
                        module('test:test5:1.0')
                        module('test:test6:1.0')
                    }
                }
            }
        }
    }

    // This test is documenting behaviour for backwards compatibility purposes
    def "can reference 'default' configuration to include runtime dependencies of module"() {
        def notRequired = mavenRepo.module('test', 'dont-include-me', '1.0')
        def m1 = mavenRepo.module('test', 'test1', '1.0').publish()
        def m2 = mavenRepo.module('test', 'test2', '1.0').publish()
        mavenRepo.module('test', 'target', '1.0')
            .dependsOn(m1, scope: 'compile')
            .dependsOn(m2, scope: 'runtime')
            .dependsOn(notRequired, scope: 'test')
            .dependsOn(notRequired, scope: 'provided')
            .publish()

        buildFile << """
dependencies {
    conf group: 'test', name: 'target', version: '1.0', configuration: 'default'
}
"""
        expect:
        succeeds 'checkDep'
        resolve.expectGraph {
            root(':', ':testproject:') {
                module('test:target:1.0') {
                    module('test:test1:1.0')
                    module('test:test2:1.0')
                }
            }
        }
    }

    // This test is documenting behaviour for backwards compatibility purposes
    def "can reference 'optional' configuration to include optional dependencies of module"() {
        def notRequired = mavenRepo.module('test', 'dont-include-me', '1.0')
        def m1 = mavenRepo.module('test', 'test1', '1.0').publish()
        def m2 = mavenRepo.module('test', 'test2', '1.0').publish()
        mavenRepo.module('test', 'target', '1.0')
            .dependsOn(notRequired, scope: 'compile')
            .dependsOn(notRequired, scope: 'runtime')
            .dependsOn(m1, scope: 'compile', optional: true)
            .dependsOn(m2, scope: 'runtime', optional: true)
            .publish()

        buildFile << """
dependencies {
    conf group: 'test', name: 'target', version: '1.0', configuration: 'optional'
}
"""
        expect:
        succeeds 'checkDep'
        resolve.expectGraph {
            root(':', ':testproject:') {
                module('test:target:1.0') {
                    configuration = 'optional'
                    module('test:test1:1.0')
                    module('test:test2:1.0')
                }
            }
        }
    }

    // This test is documenting behaviour for backwards compatibility purposes
    def "can reference 'master' configuration to include artifact only"() {
        def notRequired = mavenRepo.module('test', 'dont-include-me', '1.0')
        mavenRepo.module('test', 'target', '1.0')
            .dependsOn(notRequired, scope: 'compile')
            .dependsOn(notRequired, scope: 'runtime')
            .dependsOn(notRequired, scope: 'test')
            .dependsOn(notRequired, scope: 'provided')
            .publish()

        buildFile << """
dependencies {
    conf group: 'test', name: 'target', version: '1.0', configuration: 'master'
}
"""
        expect:
        succeeds 'checkDep'
        resolve.expectGraph {
            root(':', ':testproject:') {
                module('test:target:1.0') {
                    configuration = 'master'
                }
            }
        }
    }

    def "fails when referencing a scope that does not exist"() {
        mavenRepo.module('test', 'target', '1.0')
            .publish()

        buildFile << """
dependencies {
    conf group: 'test', name: 'target', version: '1.0', configuration: 'x86_windows'
}
"""
        expect:
        fails 'checkDep'
        failure.assertHasCause("Could not resolve test:target:1.0.\nRequired by:\n    project :")
        failure.assertHasCause("A dependency was declared on configuration 'x86_windows' which is not declared in the descriptor for test:target:1.0.")
    }
}
