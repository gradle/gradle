/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.integtests.resolve.ivy

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture

class IvyDescriptorResolveIntegrationTest extends AbstractHttpDependencyResolutionTest {
    ResolveTestFixture resolve = new ResolveTestFixture(testDirectory)

    def setup() {
        settingsFile """
            rootProject.name = 'test'
        """
        buildFile << """
            configurations {
                compile
            }
            ${resolve.configureProject("compile")}
        """
    }

    def "substitutes system properties into ivy descriptor"() {
        given:
        ivyRepo.module("org.gradle", "test", "1.45")
                .dependsOn('org.gradle.${sys_prop}', 'module_${sys_prop}', 'v_${sys_prop}')
                .publish()

        ivyRepo.module("org.gradle.111", "module_111", "v_111").publish()

        and:
        buildFile << """
repositories { ivy { url = "${ivyRepo.uri}" } }
dependencies {
    compile "org.gradle:test:1.45"
}

task check {
    def files = configurations.compile
    doLast {
        configurations.compile.resolvedConfiguration.firstLevelModuleDependencies.each {
            it.children.each { transitive ->
                println "transitive.moduleGroup=\${transitive.moduleGroup}"
                println "transitive.moduleName=\${transitive.moduleName}"
                println "transitive.moduleVersion=\${transitive.moduleVersion}"
            }
        }
        println files.collect { it.name }
    }
}
"""

        when:
        executer.withArgument("-Dsys_prop=111")
        run "checkDeps", "check"

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org.gradle:test:1.45") {
                    module("org.gradle.111:module_111:v_111")
                }
            }
        }
        outputContains("transitive.moduleGroup=org.gradle.111")
        outputContains("transitive.moduleName=module_111")
        outputContains("transitive.moduleVersion=v_111")
        outputContains("[test-1.45.jar, module_111-v_111.jar]")
    }

    def "merges values from parent descriptor file that is available locally"() {
        given:
        def parentModule = ivyHttpRepo.module("org.gradle.parent", "parent_module", "1.1").dependsOn("org.gradle.dep", "dep_module", "1.1").publish()
        def depModule = ivyHttpRepo.module("org.gradle.dep", "dep_module", "1.1").publish()

        def dep = ivyHttpRepo.module("org.gradle", "test", "1.45")
        dep.extendsFrom(organisation: "org.gradle.parent", module: "parent_module", revision: "1.1", location: parentModule.ivyFile.toURI().toURL())
        parentModule.publish()
        dep.publish()

        buildFile << """
repositories { ivy { url = "${ivyHttpRepo.uri}" } }
dependencies {
    compile "org.gradle:test:1.45"
}

task check {
    def files = configurations.compile
    doLast {
        println files.collect { it.name }
    }
}
"""

        and:
        dep.ivy.expectGet()
        depModule.ivy.expectGet()
        dep.jar.expectGet()
        depModule.jar.expectGet()

        when:
        run "checkDeps", "check"

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org.gradle:test:1.45") {
                    module("org.gradle.dep:dep_module:1.1")
                }
            }
        }
        outputContains("[test-1.45.jar, dep_module-1.1.jar]")

        when:
        server.resetExpectations()
        run "checkDeps", "check"

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org.gradle:test:1.45") {
                    module("org.gradle.dep:dep_module:1.1")
                }
            }
        }
        outputContains("[test-1.45.jar, dep_module-1.1.jar]")
    }

    def "merges values from parent descriptor file"() {
        given:
        final parentModule = ivyHttpRepo.module("org.gradle.parent", "parent_module", "1.1").dependsOn("org.gradle.dep", "dep_module", "1.1").publish()
        final depModule = ivyHttpRepo.module("org.gradle.dep", "dep_module", "1.1").publish()

        final dep = ivyHttpRepo.module("org.gradle", "test", "1.45")
        final extendAttributes = [organisation: "org.gradle.parent", module: "parent_module", revision: "1.1"]
        dep.extendsFrom(extendAttributes)
        parentModule.publish()
        dep.publish()

        buildFile << """
repositories { ivy { url = "${ivyHttpRepo.uri}" } }
dependencies {
    compile "org.gradle:test:1.45"
}

task check {
    def files = configurations.compile
    doLast {
        println files.collect { it.name }
    }
}
"""

        and:
        dep.ivy.expectGet()
        parentModule.ivy.expectGet()
        depModule.ivy.expectGet()
        dep.jar.expectGet()
        depModule.jar.expectGet()

        when:
        run "checkDeps", "check"

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org.gradle:test:1.45") {
                    module("org.gradle.dep:dep_module:1.1")
                }
            }
        }
        outputContains("[test-1.45.jar, dep_module-1.1.jar]")

        when:
        server.resetExpectations()
        run "checkDeps", "check"

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org.gradle:test:1.45") {
                    module("org.gradle.dep:dep_module:1.1")
                }
            }
        }
        outputContains("[test-1.45.jar, dep_module-1.1.jar]")
    }
}
