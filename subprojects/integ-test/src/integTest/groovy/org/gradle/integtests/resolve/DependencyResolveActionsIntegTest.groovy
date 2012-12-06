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

import org.gradle.integtests.fixtures.AbstractIntegrationTest
import org.junit.Test

/**
 * @author Szczepan Faber, @date 03.03.11
 */
class DependencyResolveActionsIntegTest extends AbstractIntegrationTest {

    @Test
    void "forces modules by action"()
    {
        repo.module("org.utils", "impl", '1.3').dependsOn('org.utils', 'api', '1.3').publish()
        repo.module("org.utils", "impl", '1.5').dependsOn('org.utils', 'api', '1.5').publish()

        repo.module("org.utils", "api", '1.3').publish()
        repo.module("org.utils", "api", '1.5').publish()

        repo.module("org.stuff", "foo", '2.0').dependsOn('org.utils', 'api', '1.5') publish()
        repo.module("org.utils", "optional-lib", '5.0').publish()

        //above models the scenario where org.utils:api and org.utils:impl are libraries that must be resolved with the same version
        //however due to the conflict resolution, org.utils:api:1.5 and org.utils.impl:1.3 are resolved.

        def buildFile = file("build.gradle")
        buildFile << """
            configurations { conf }
            repositories {
                maven { url "${repo.uri}" }
            }

            dependencies {
                conf 'org.stuff:foo:2.0', 'org.utils:impl:1.3', 'org.utils:optional-lib:5.0'
            }

            configurations.conf.resolutionStrategy {
	            eachDependency {
                    if (it.requested.group == 'org.utils' && it.requested.name != 'optional-lib') {
                        it.forceVersion '1.5'
                    }
	            }
	            failOnVersionConflict()
	        }
"""

        //when
        executer.withTasks("dependencies").run()

        //then no exceptions are thrown

        //TODO SF more coverge, split tests
    }

    def getRepo() {
        return maven(file("repo"))
    }
}
