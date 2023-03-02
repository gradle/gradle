/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.integtests.resolve.api

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import org.gradle.integtests.fixtures.extensions.FluidDependenciesResolveTest
import spock.lang.Issue

@FluidDependenciesResolveTest
class ExtendingConfigurationsIntegrationTest extends AbstractDependencyResolutionTest {

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

            task check {
                doLast {
                    configurations.conf.extendsFrom(configurations.fooConf)
                    assert configurations.conf.allDependencies*.name == ['foo']

                    //purposefully again:
                    configurations.conf.extendsFrom(configurations.fooConf)
                    assert configurations.conf.allDependencies*.name == ['foo']

                    //replace:
                    configurations.conf.extendsFrom = [configurations.barConf] as Set
                    assert configurations.conf.allDependencies*.name == ['bar']
                }
            }
        """

        when:
        run "check"

        then:
        noExceptionThrown()
    }

    def "resolving parent configuration does not impact iteration order for child configuration"() {
        mavenRepo.module("org", "foo").publish()
        mavenRepo.module("org", "bar").publish()
        mavenRepo.module("org", "baz").publish()

        buildFile << """
repositories {
    maven { url "${mavenRepo.uri}" }
}
configurations {
    one
    child.extendsFrom one
    two
    child.extendsFrom two
    zzz
    one.extendsFrom zzz
}
dependencies {
    one "org:foo:1.0"
    two "org:bar:1.0"
    zzz "org:baz:1.0"
}

task checkResolveChild {
    doFirst {
        assert configurations.child.files*.name == ['foo-1.0.jar', 'bar-1.0.jar', 'baz-1.0.jar']
    }
}

task checkResolveParentThenChild {
    doFirst {
        assert configurations.two.files*.name == ['bar-1.0.jar']
        assert configurations.one.files*.name == ['foo-1.0.jar', 'baz-1.0.jar']
        assert configurations.child.files*.name == ['foo-1.0.jar', 'bar-1.0.jar', 'baz-1.0.jar']
    }
}
"""

        expect:
        succeeds "checkResolveChild"
        succeeds "checkResolveParentThenChild"
    }

    @Issue("https://github.com/gradle/gradle/issues/24109")
    def "can resolve configuration after extending a resolved configuration"() {
        given:
        mavenRepo.module("org", "foo").publish()

        buildFile << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            configurations {
                superConfiguration
                subConfiguration
            }
            dependencies {
                superConfiguration 'org:foo:1.0'
            }

            task resolve {
                println configurations.superConfiguration.files.collect { it.name }
                configurations.subConfiguration.extendsFrom(configurations.superConfiguration)
                println configurations.subConfiguration.files.collect { it.name }
            }
        """

        when:
        succeeds("resolve")

        then:
        output.contains("[foo-1.0.jar]\n[foo-1.0.jar]")
    }
}
