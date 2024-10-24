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
    ResolveTestFixture resolve = new ResolveTestFixture(buildFile, "compile")

    def setup() {
        settingsFile """
            rootProject.name = 'test'
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
configurations { compile }
dependencies {
    compile "org.gradle:test:1.45"
}

task check {
    doLast {
        configurations.compile.resolvedConfiguration.firstLevelModuleDependencies.each {
            it.children.each { transitive ->
                assert transitive.moduleGroup == "org.gradle.111"
                assert transitive.moduleName == "module_111"
                assert transitive.moduleVersion == "v_111"
            }
        }
        assert configurations.compile.collect { it.name } == ['test-1.45.jar', 'module_111-v_111.jar']
    }
}
"""
        resolve.prepare()

        when:
        executer.withArgument("-Dsys_prop=111")
        run "checkDeps"

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org.gradle:test:1.45") {
                    module("org.gradle.111:module_111:v_111")
                }
            }
        }
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
configurations { compile }
dependencies {
    compile "org.gradle:test:1.45"
}

task check {
    doLast {
        assert configurations.compile.collect { it.name } == ['test-1.45.jar', 'dep_module-1.1.jar']
    }
}
"""
        resolve.prepare()

        and:
        dep.ivy.expectGet()
        depModule.ivy.expectGet()
        dep.jar.expectGet()
        depModule.jar.expectGet()

        when:
        run "checkDeps"

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org.gradle:test:1.45") {
                    module("org.gradle.dep:dep_module:1.1")
                }
            }
        }

        when:
        server.resetExpectations()
        run "checkDeps"

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org.gradle:test:1.45") {
                    module("org.gradle.dep:dep_module:1.1")
                }
            }
        }
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
configurations { compile }
dependencies {
    compile "org.gradle:test:1.45"
}

task check {
    doLast {
        assert configurations.compile.collect { it.name } == ['test-1.45.jar', 'dep_module-1.1.jar']
    }
}
"""
        resolve.prepare()

        and:
        dep.ivy.expectGet()
        parentModule.ivy.expectGet()
        depModule.ivy.expectGet()
        dep.jar.expectGet()
        depModule.jar.expectGet()

        when:
        run "checkDeps"

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org.gradle:test:1.45") {
                    module("org.gradle.dep:dep_module:1.1")
                }
            }
        }

        when:
        server.resetExpectations()
        run "checkDeps"

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org.gradle:test:1.45") {
                    module("org.gradle.dep:dep_module:1.1")
                }
            }
        }
    }
}
