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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.hamcrest.CoreMatchers
import spock.lang.Issue

class DependencyNotationIntegrationSpec extends AbstractIntegrationSpec {

    @ToBeFixedForConfigurationCache(because = "Task uses the Configuration API")
    def "understands dependency notations"() {
        when:
        buildFile <<  """
configurations {
    conf
    gradleStuff
    allowsCollections
}

def someDependency = project.dependencies.create(files('foo.txt'))
dependencies {
    conf someDependency
    conf "org.mockito:mockito-core:1.8"
    conf group: 'org.spockframework', name: 'spock-core', version: '1.0'
    conf provider { "junit:junit:4.12" }

    conf('org.test:configured') {
        version {
           prefer '1.1'
        }
        transitive = false
    }

    gradleStuff gradleApi()

    allowsCollections "org.mockito:mockito-core:1.8", someDependency
}

task checkDeps {
    doLast {
        def deps = configurations.conf.incoming.dependencies
        assert deps.contains(someDependency)
        assert deps.find { it instanceof ExternalDependency && it.group == 'org.mockito' && it.name == 'mockito-core' && it.version == '1.8'  }
        assert deps.find { it instanceof ExternalDependency && it.group == 'org.spockframework' && it.name == 'spock-core' && it.version == '1.0'  }
        assert deps.find { it instanceof ExternalDependency && it.group == 'junit' && it.name == 'junit' && it.version == '4.12' }
        def configuredDep = deps.find { it instanceof ExternalDependency && it.group == 'org.test' && it.name == 'configured' }
        assert configuredDep.version == '1.1'
        assert configuredDep.transitive == false

        deps = configurations.gradleStuff.dependencies
        assert deps.findAll { it instanceof SelfResolvingDependency }.size() > 0 : "should include gradle api jars"

        deps = configurations.allowsCollections.dependencies
        assert deps.size() == 2
        assert deps.find { it instanceof ExternalDependency && it.group == 'org.mockito' }
        assert deps.contains(someDependency)
    }
}
"""
        then:
        succeeds 'checkDeps'
    }

    @ToBeFixedForConfigurationCache(because = "Task uses the Configuration API")
    def "understands project notations"() {
        when:
        settingsFile << "include 'otherProject'"

        buildFile <<  """
            configurations {
                conf
                confTwo
            }
            dependencies {
                conf project(':otherProject')
                confTwo project(path: ':otherProject', configuration: 'otherConf')
            }

            task checkDeps {
                doLast {
                    def deps = configurations.conf.incoming.dependencies
                    assert deps.size() == 1
                    assert deps.find { it.path == ':otherProject' && it.targetConfiguration == null }

                    deps = configurations.confTwo.incoming.dependencies
                    assert deps.size() == 1
                    assert deps.find { it.path == ':otherProject' && it.targetConfiguration == 'otherConf' }
                }
            }
        """

        file("otherProject/build.gradle") << """
            configurations {
                otherConf
            }
        """

        then:
        succeeds 'checkDeps'
    }

    def "fails gracefully for invalid notations"() {
        when:
        buildFile <<  """
configurations {
    conf
}

dependencies {
    conf 100
}

task checkDeps
"""
        then:
        fails 'checkDeps'
        failure.assertThatCause(CoreMatchers.startsWith("Cannot convert the provided notation to an object of type Dependency: 100."))
    }

    def "fails gracefully for single null notation"() {
        when:
        buildFile <<  """
configurations {
    conf
}

dependencies {
    conf null
}

task checkDeps
"""
        then:
        fails 'checkDeps'
        failure.assertThatCause(CoreMatchers.startsWith("Cannot convert a null value to an object of type Dependency"))
    }

    def "fails gracefully for null notation in list"() {
        when:
        buildFile <<  """
configurations {
    conf
}

dependencies {
    conf "a:b:c", null, "d:e:f"
}

task checkDeps
"""
        then:
        fails 'checkDeps'
        failure.assertThatCause(CoreMatchers.startsWith("Cannot convert a null value to an object of type Dependency"))
    }

    @Issue("https://issues.gradle.org/browse/GRADLE-3271")
    def "gradle API equality"() {
        when:
        buildFile << """
            configurations {
                conf
            }

            dependencies {
                conf gradleApi()
            }

            assert dependencies.gradleApi().contentEquals(dependencies.gradleApi())
            assert dependencies.gradleApi().is(dependencies.gradleApi())
            assert dependencies.gradleApi() == dependencies.gradleApi()
            assert configurations.conf.dependencies.contains(dependencies.gradleApi())
        """

        then:
        executer.expectDocumentedDeprecationWarning("The Dependency.contentEquals(Dependency) method has been deprecated. This is scheduled to be removed in Gradle 9.0. Use Object.equals(Object) instead Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#deprecated_content_equals")
        succeeds("help")
    }

    @ToBeFixedForConfigurationCache(because = "Task uses the Configuration API")
    def "dependencies block supports provider dependencies"() {
        when:
        buildFile << """
            configurations {
              conf
            }

            dependencies {
              conf provider { gradleApi() }
            }

            task check {
                doLast {
                    assert configurations.conf.dependencies.contains(dependencies.gradleApi())
                }
            }
        """
        then:
        succeeds "check"
    }

    def "fails if using a configuration as a dependency"() {
        given:
        buildFile << """
            configurations {
              conf
              other
            }

            dependencies {
              conf configurations.other
            }

        """

        when:
        fails "dependencies", '--configuration', 'conf'

        then:
        result.hasErrorOutput("Adding a Configuration as a dependency is no longer allowed as of Gradle 8.0.")
    }

    def "contentEquals is deprecated"() {
        buildFile << """
            def d1 = dependencies.create(files())
            def d2 = dependencies.create('org.foo:baz:1.0')
            def d3 = dependencies.create(project)

            def other = dependencies.create('org.other:foo:1.0')

            d1.contentEquals(other)
            d2.contentEquals(other)
            d3.contentEquals(other)
        """

        expect:
        3.times { executer.expectDocumentedDeprecationWarning("The Dependency.contentEquals(Dependency) method has been deprecated. This is scheduled to be removed in Gradle 9.0. Use Object.equals(Object) instead Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#deprecated_content_equals") }
        succeeds("help")
    }
}
