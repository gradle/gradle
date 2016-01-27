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
package org.gradle.integtests.resolve;

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import org.gradle.integtests.fixtures.FluidDependenciesResolveRunner
import org.junit.runner.RunWith
import spock.lang.Issue;

@RunWith(FluidDependenciesResolveRunner)
public class ExtendingConfigurationsIntegrationTest extends AbstractDependencyResolutionTest {

    @Issue("GRADLE-2873")
    def "may replace configuration extension targets"() {
        mavenRepo.module("org", "foo").publish()
        mavenRepo.module("org", "bar").publish()

        buildFile << """
            configurations {
                fooConf
                barConf
                conf
            }

            dependencies {
                fooConf 'org:foo:1.0'
                barConf 'org:bar:1.0'
            }

            task check << {
                configurations.conf.extendsFrom(configurations.fooConf)
                assert configurations.conf.allDependencies*.name == ['foo']

                //purposefully again:
                configurations.conf.extendsFrom(configurations.fooConf)
                assert configurations.conf.allDependencies*.name == ['foo']

                //replace:
                configurations.conf.extendsFrom = [configurations.barConf] as Set
                assert configurations.conf.allDependencies*.name == ['bar']
            }
        """

        when:
        run "check"

        then:
        noExceptionThrown()
    }
}
