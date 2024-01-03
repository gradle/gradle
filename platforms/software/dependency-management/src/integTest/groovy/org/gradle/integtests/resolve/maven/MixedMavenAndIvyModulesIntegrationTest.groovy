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

class MixedMavenAndIvyModulesIntegrationTest extends AbstractDependencyResolutionTest {
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
                ivy { url '${ivyRepo.uri}' }
            }
            configurations {
                conf
            }
"""
    }

    def "when no target configuration is specified then a dependency on maven module includes the default configuration of required ivy module"() {
        def inDefault = ivyRepo.module("org.test", "in-default", "1.0").publish()
        def m1 = ivyRepo.module("org.test", "m1", "1.0")
            .configuration("compile")
            .publish()
        def m2 = ivyRepo.module("org.test", "m2", "1.0").publish()
        def m3 = ivyRepo.module("org.test", "m3", "1.0")
            .configuration("master")
            .publish()
        def ivyModule = ivyRepo.module("org.test", "ivy", "1.0")
            .configuration("compile")
            .configuration("runtime")
            .configuration("master")
            .configuration("other")
            .configuration("default")
            .dependsOn(m1, conf: "compile")
            .dependsOn(m2, conf: "runtime")
            .dependsOn(m3, conf: "master")
            .dependsOn(inDefault, conf: "other,default")
            .artifact(name: "compile", conf: "compile")
            .artifact(name: "runtime", conf: "runtime")
            .artifact(name: "master", conf: "master")
            .artifact(name: 'in-default', conf: "other,default")
            .publish()
        mavenRepo.module("org.test", "maven", "1.0")
            .dependsOn(ivyModule)
            .publish()

        buildFile << """
dependencies {
    conf 'org.test:maven:1.0'
}
"""
        expect:
        succeeds 'checkDep'
        resolve.expectGraph {
            root(':', ':testproject:') {
                module('org.test:maven:1.0:runtime') {
                    module('org.test:ivy:1.0') {
                        artifact(name: 'in-default')
                        module('org.test:in-default:1.0')
                    }
                }
            }
        }
    }

    def "a dependency on compile scope of maven module includes default of required ivy module when they are present"() {
        def inDefault = ivyRepo.module("org.test", "in-default", "1.0").publish()
        def m1 = ivyRepo.module("org.test", "m1", "1.0")
            .configuration("compile")
            .publish()
        def m2 = ivyRepo.module("org.test", "m2", "1.0").publish()
            .configuration("master")
            .publish()
        def ivyModule = ivyRepo.module("org.test", "ivy", "1.0")
            .configuration("compile")
            .configuration("runtime")
            .configuration("master")
            .configuration("other")
            .configuration("default")
            .dependsOn(m1, conf: "compile")
            .dependsOn(m2, conf: "master")
            .dependsOn(inDefault, conf: "other,default,runtime")
            .artifact(name: "compile", conf: "compile")
            .artifact(name: "master", conf: "master")
            .artifact(name: 'in-default', conf: "other,default,runtime")
            .publish()
        mavenRepo.module("org.test", "maven", "1.0")
            .dependsOn(ivyModule)
            .publish()

        buildFile << """
dependencies {
    conf group: 'org.test', name: 'maven', version: '1.0', configuration: 'compile'
}
"""
        expect:
        succeeds 'checkDep'
        resolve.expectGraph {
            root(':', ':testproject:') {
                module('org.test:maven:1.0') {
                    configuration = 'compile'
                    module('org.test:ivy:1.0') {
                        module('org.test:in-default:1.0')
                        artifact(name: 'in-default')
                    }
                }
            }
        }
    }

    def "ignores missing master configuration of ivy module when consumed by maven module"() {
        def inDefault = ivyRepo.module("org.test", "in-default", "1.0").publish()
        def m1 = ivyRepo.module("org.test", "m1", "1.0").publish()
        def m2 = ivyRepo.module("org.test", "m2", "1.0").publish()
        def ivyModule = ivyRepo.module("org.test", "ivy", "1.0")
            .configuration("compile")
            .configuration("runtime")
            .configuration("other")
            .configuration("default")
            .dependsOn(m1, conf: "compile->default")
            .dependsOn(m2, conf: "runtime->default")
            .dependsOn(inDefault, conf: "*,!compile,!runtime")
            .artifact(name: "compile", conf: "compile")
            .artifact(name: "runtime", conf: "runtime")
            .artifact(name: 'in-default', conf: "other,default")
            .publish()
        mavenRepo.module("org.test", "maven", "1.0")
            .dependsOn(ivyModule)
            .publish()

        buildFile << """
dependencies {
    conf 'org.test:maven:1.0'
}
"""
        expect:
        succeeds 'checkDep'
        resolve.expectGraph {
            root(':', ':testproject:') {
                module('org.test:maven:1.0:runtime') {
                    module('org.test:ivy:1.0') {
                        module('org.test:in-default:1.0')
                        artifact(name: 'in-default')
                    }
                }
            }
        }
    }

    def "ivy module can consume scopes of maven module"() {
        def notRequired = mavenRepo.module("org.test", "ignore-me", "1.0")
        def m1 = mavenRepo.module('org.test', 'm1', '1.0').publish()
        def m2 = mavenRepo.module('org.test', 'm2', '1.0').publish()
        def mavenModule = mavenRepo.module("org.test", "maven", "1.0")
            .dependsOn(m1, scope: "compile")
            .dependsOn(m2, scope: "runtime")
            .dependsOn(notRequired, scope: "test")
            .dependsOn(notRequired, scope: "provided")
            .publish()
        ivyRepo.module("org.test", "ivy", "1.0")
            .configuration("other")
            .configuration("compile")
            .dependsOn(mavenModule, conf: "compile->compile;runtime->runtime,master")
            .dependsOn(notRequired, conf: "other")
            .publish()

        buildFile << """
dependencies {
    conf group: 'org.test', name: 'ivy', version: '1.0'
}
"""
        expect:
        succeeds 'checkDep'
        resolve.expectGraph {
            root(':', ':testproject:') {
                module('org.test:ivy:1.0') {
                    module('org.test:maven:1.0') {
                        module('org.test:m1:1.0')
                        module('org.test:m2:1.0')
                    }
                }
            }
        }
    }

    def "selects default configuration of ivy module when dependency from consuming maven module is substituted"() {
        def m1 = ivyRepo.module("org.test", "m1", "1.0")
            .configuration("compile")
            .publish()
        def m2 = ivyRepo.module("org.test", "m2", "1.0").publish()
            .configuration("master")
            .publish()
        def m3 = ivyRepo.module("org.test", "m3", "1.0").publish()
            .configuration("master")
            .publish()
        ivyRepo.module("org.test", "ivy", "1.2")
            .configuration("compile")
            .configuration("runtime")
            .configuration("master")
            .configuration("other")
            .configuration("default")
            .dependsOn(m1, conf: "compile")
            .dependsOn(m2, conf: "master")
            .dependsOn(m3, conf: "*,!compile,!master")
            .artifact(name: "compile", conf: "compile")
            .artifact(name: "master", conf: "master")
            .artifact(name: 'default', conf: "other,default,runtime")
            .publish()
        def ivyModule = ivyRepo.module("org.test", "ivy", "1.0")
        mavenRepo.module("org.test", "maven", "1.0")
            .dependsOn(ivyModule)
            .publish()

        buildFile << """
dependencies {
    conf group: 'org.test', name: 'maven', version: '1.0', configuration: 'compile'
}
configurations.conf.resolutionStrategy.force('org.test:ivy:1.2')
"""
        expect:
        succeeds 'checkDep'
        resolve.expectGraph {
            root(':', ':testproject:') {
                module('org.test:maven:1.0') {
                    configuration = 'compile'
                    edge('org.test:ivy:1.0', 'org.test:ivy:1.2') {
                        forced()
                        artifact(name: 'default')
                        module('org.test:m3:1.0')
                    }
                }
            }
        }
    }

    def "selects default configuration of ivy module when dynamic dependency is used from consuming maven module"() {
        def inDefault = ivyRepo.module("org.test", "in-default", "1.0").publish()
        def m1 = ivyRepo.module("org.test", "m1", "1.0")
            .configuration("compile")
            .publish()
        def m2 = ivyRepo.module("org.test", "m2", "1.0").publish()
            .configuration("master")
            .publish()
        ivyRepo.module("org.test", "ivy", "1.2")
            .configuration("compile")
            .configuration("runtime")
            .configuration("master")
            .configuration("other")
            .configuration("default")
            .dependsOn(m1, conf: "compile")
            .dependsOn(m2, conf: "master")
            .dependsOn(inDefault, conf: "*,!compile,!master")
            .artifact(name: "compile", conf: "compile")
            .artifact(name: "master", conf: "master")
            .artifact(name: 'in-default', conf: "other,default,runtime")
            .publish()
        mavenRepo.module("org.test", "maven", "1.0")
            .dependsOn("org.test", "ivy", "1.+")
            .publish()

        buildFile << """
dependencies {
    conf group: 'org.test', name: 'maven', version: '1.0', configuration: 'compile'
}
"""
        expect:
        succeeds 'checkDep'
        resolve.expectGraph {
            root(':', ':testproject:') {
                module('org.test:maven:1.0:runtime') {
                    configuration = 'compile'
                    edge('org.test:ivy:1.+', 'org.test:ivy:1.2') {
                        artifact(name: 'in-default')
                        module('org.test:in-default:1.0')
                    }
                }
            }
        }
    }

    def "can interleave ivy and maven modules"() {
        def m1 = ivyRepo.module('org.test', 'm1', '1.0').publish()
        def m2 = mavenRepo.module('org.test', 'm2', '1.0')
            .dependsOn(m1)
            .publish()
        def m3 = ivyRepo.module('org.test', 'm3', '1.0')
            .dependsOn(m2)
            .publish()
        mavenRepo.module('org.test', 'm4', '1.0')
            .dependsOn(m3)
            .publish()

        buildFile << """
dependencies {
    conf group: 'org.test', name: 'm4', version: '1.0'
}
"""
        expect:
        succeeds 'checkDep'
        resolve.expectGraph {
            root(':', ':testproject:') {
                module('org.test:m4:1.0:runtime') {
                    module('org.test:m3:1.0') {
                        module('org.test:m2:1.0:runtime') {
                            module('org.test:m1:1.0')
                        }
                    }
                }
            }
        }
    }
}
