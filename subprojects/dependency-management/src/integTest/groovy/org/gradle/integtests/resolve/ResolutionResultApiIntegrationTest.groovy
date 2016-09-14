/*
 * Copyright 2012 the original author or authors.
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



package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import org.gradle.integtests.fixtures.FluidDependenciesResolveRunner
import org.junit.runner.RunWith

@RunWith(FluidDependenciesResolveRunner)
class ResolutionResultApiIntegrationTest extends AbstractDependencyResolutionTest {

    /*
    The ResolutionResult API is also covered by the dependency report integration tests.
     */

    def "selection reasons are described"() {
        given:
        mavenRepo.module("org", "leaf", "1.0").publish()
        mavenRepo.module("org", "leaf", "2.0").publish()
        mavenRepo.module("org", "foo", "0.5").publish()

        mavenRepo.module("org", "foo", "1.0").dependsOn('org', 'leaf', '1.0').publish()
        mavenRepo.module("org", "bar", "1.0").dependsOn('org', 'leaf', '2.0').publish()
        mavenRepo.module("org", "baz", "1.0").dependsOn('org', 'foo',  '1.0').publish()

        file("settings.gradle") << "rootProject.name = 'cool-project'"

        file("build.gradle") << """
            version = '5.0'
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            configurations {
                conf
            }
            configurations.conf.resolutionStrategy.force 'org:leaf:2.0'
            dependencies {
                conf 'org:foo:0.5', 'org:bar:1.0', 'org:baz:1.0'
            }
            task resolutionResult {
                doLast {
                    def result = configurations.conf.incoming.resolutionResult
                    result.allComponents {
                        if(it.id instanceof ModuleComponentIdentifier) {
                            println it.id.module + ":" + it.id.version + " " + it.selectionReason.description
                        }
                        else if(it.id instanceof ProjectComponentIdentifier) {
                            println it.moduleVersion.name + ":" + it.moduleVersion.version + " " + it.selectionReason.description
                        }
                    }
                }
            }
        """

        when:
        run "resolutionResult"

        then:
        output.contains """
cool-project:5.0 root
foo:1.0 conflict resolution
leaf:2.0 forced
bar:1.0 requested
baz:1.0 requested
"""
    }
}
