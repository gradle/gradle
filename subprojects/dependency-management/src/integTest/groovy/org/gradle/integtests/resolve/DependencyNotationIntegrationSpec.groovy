/*
 * Copyright 2023 the original author or authors.
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
import org.gradle.test.fixtures.dsl.GradleDsl
import org.hamcrest.CoreMatchers
import spock.lang.IgnoreRest
import spock.lang.Issue

class DependencyNotationIntegrationSpec extends AbstractIntegrationSpec {

    @ToBeFixedForConfigurationCache(because = "Task uses the Configuration API")
    def "understands dependency notations"() {
        when:
        buildFile <<  """
import org.gradle.api.internal.artifacts.dependencies.*
configurations {
    conf
    gradleStuff
    allowsCollections
}

def someDependency = new DefaultSelfResolvingDependency(files('foo.txt'))
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

    conf module('org.foo:moduleOne:1.0'), module('org.foo:moduleTwo:1.0')

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

        assert deps.find { it instanceof ClientModule && it.name == 'moduleOne' && it.group == 'org.foo' }
        assert deps.find { it instanceof ClientModule && it.name == 'moduleTwo' && it.version == '1.0' }

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

    @IgnoreRest
    def "understands plugin dependency notations"() {
        when:
        buildScript( """
import org.gradle.api.internal.artifacts.dependencies.*

//buildscript
buildscript {
    ${mavenCentralRepository()}
    dependencies {
        classpath(plugin('org.jetbrains.kotlin.js')) {
            version {
                prefer '1.8.0'
            }
        }
        classpath(plugin('org.jetbrains.kotlin.jvm', '1.8.0'))
    }
}

plugins {
    id 'java'
    id 'jvm-test-suite'
}

// main dependency block
configurations {
    conf
}

dependencies {

    conf(plugin('org.jetbrains.kotlin.js')) {
        version {
            prefer '1.8.0'
        }
        transitive = false
    }
     conf plugin('org.jetbrains.kotlin.jvm', '1.8.0')
     conf plugin('org.jetbrains.kotlin.multiplatform')
}

// test-suite dependencies
testing {
    suites {
        test {
            dependencies {
                implementation.plugin('org.jetbrains.kotlin.js') {
                    version {
                        prefer '1.8.0'
                    }
                    transitive = false
                }
                implementation.plugin('org.jetbrains.kotlin.jvm', '1.8.0')
                implementation.plugin('org.jetbrains.kotlin.multiplatform')
            }
        }
    }
}

task checkDeps {
    doLast {
        // buildscript
        buildscript.configurations.classpath.incoming.dependencies.find { it instanceof ExternalDependency && it.group == 'org.jetbrains.kotlin.js' && it.name == 'org.jetbrains.kotlin.js.gradle.plugin' && it.version == '1.8.0'  }
        buildscript.configurations.classpath.incoming.dependencies.find { it instanceof ExternalDependency && it.group == 'org.jetbrains.kotlin.jvm' && it.name == 'org.jetbrains.kotlin.jvm.gradle.plugin' && it.version == '1.8.0' }

        // main dependency block
        def deps = configurations.conf.incoming.dependencies

        def configuredDep = deps.find { it instanceof ExternalDependency && it.group == 'org.jetbrains.kotlin.js' && it.name == 'org.jetbrains.kotlin.js.gradle.plugin' }
        assert configuredDep.version == '1.8.0'
        assert configuredDep.transitive == false
        assert deps.find { it instanceof ExternalDependency && it.group == 'org.jetbrains.kotlin.jvm' && it.name == 'org.jetbrains.kotlin.jvm.gradle.plugin' && it.version == '1.8.0' }
        assert deps.find { it instanceof ExternalDependency && it.group == 'org.jetbrains.kotlin.multiplatform' && it.name == 'org.jetbrains.kotlin.multiplatform.gradle.plugin' }

        // test-suite dependencies
        def testDeps = configurations.testImplementation.incoming.dependencies

        def configuredTestDep = testDeps.find { it instanceof ExternalDependency && it.group == 'org.jetbrains.kotlin.js' && it.name == 'org.jetbrains.kotlin.js.gradle.plugin'}
        assert configuredTestDep.version == '1.8.0'
        assert configuredTestDep.transitive == false
        assert testDeps.find { it instanceof ExternalDependency && it.group == 'org.jetbrains.kotlin.jvm' && it.name == 'org.jetbrains.kotlin.jvm.gradle.plugin' && it.version == '1.8.0' }
        assert testDeps.find { it instanceof ExternalDependency && it.group == 'org.jetbrains.kotlin.multiplatform' && it.name == 'org.jetbrains.kotlin.multiplatform.gradle.plugin' }
    }
}
""")
        then:
        succeeds 'checkDeps'
    }

    @IgnoreRest
    def "understands plugin dependency notations in kotlin"() {
        when:
        buildKotlinFile <<  """
import org.gradle.api.internal.artifacts.dependencies.*

// buildscript
buildscript {
    repositories {
        ${mavenCentralRepository(GradleDsl.KOTLIN)}
    }
    dependencies {
        add("classpath", plugin("org.jetbrains.kotlin.js")) {
            version {
                prefer("1.8.0")
            }
        }
        add("classpath", plugin("org.jetbrains.kotlin.jvm", "1.8.0"))
    }
}

plugins {
    java
    `jvm-test-suite`
}

// main dependency block
configurations {
    create("conf")
}

dependencies {

    add("conf", plugin("org.jetbrains.kotlin.multiplatform")) {
        version {
            prefer("1.8.0")
        }
        isTransitive = false
    }
    add("conf", plugin(id = "org.jetbrains.kotlin.multiplatform.pm20", version = "1.8.0"))
    add("conf", plugin("org.jetbrains.kotlin.plugin.parcelize", "1.8.0"))
    add("conf", plugin("org.jetbrains.kotlin.plugin.scripting"))
}

// test-suite dependencies
testing {
    suites {
        val test by getting(JvmTestSuite::class) {
            dependencies {
                implementation.plugin("org.jetbrains.kotlin.js") {
                    version {
                        prefer("1.8.0")
                    }
                    isTransitive = false
                }
                implementation.plugin("org.jetbrains.kotlin.jvm", "1.8.0")
                implementation.plugin("org.jetbrains.kotlin.multiplatform")
            }
        }
    }
}

tasks.register("checkDeps") {
    doLast {
        // buildscript
        buildscript.configurations.getByName("classpath").incoming.dependencies.find { it is ExternalDependency && it.group == "org.jetbrains.kotlin.js" && it.name == "org.jetbrains.kotlin.js.gradle.plugin" && it.version == "1.8.0" }
        buildscript.configurations.getByName("classpath").incoming.dependencies.find { it is ExternalDependency && it.group == "org.jetbrains.kotlin.jvm" && it.name == "org.jetbrains.kotlin.jvm.gradle.plugin" && it.version == "1.8.0" }

        // main dependency block
        val deps = configurations.get("conf").incoming.dependencies

        val configuredDep = deps.single { it is ExternalDependency && it.group == "org.jetbrains.kotlin.multiplatform" && it.name == "org.jetbrains.kotlin.multiplatform.gradle.plugin" }
        configuredDep as ExternalDependency
        require(configuredDep.version == "1.8.0")
        require(configuredDep.isTransitive == false)

        deps.single { it is ExternalDependency && it.group == "org.jetbrains.kotlin.multiplatform.pm20" && it.name == "org.jetbrains.kotlin.multiplatform.pm20.gradle.plugin" && it.version == "1.8.0" }
        deps.single { it is ExternalDependency && it.group == "org.jetbrains.kotlin.plugin.parcelize" && it.name == "org.jetbrains.kotlin.plugin.parcelize.gradle.plugin" && it.version == "1.8.0" }
        deps.single { it is ExternalDependency && it.group == "org.jetbrains.kotlin.plugin.scripting" && it.name == "org.jetbrains.kotlin.plugin.scripting.gradle.plugin"  }

        // test-suite dependencies
        val testDeps = configurations.get("testImplementation").incoming.dependencies

        val configuredTestDep = testDeps.single { it is ExternalDependency && it.group == "org.jetbrains.kotlin.js" && it.name == "org.jetbrains.kotlin.js.gradle.plugin" }
        configuredTestDep as ExternalDependency
        require(configuredTestDep.version == "1.8.0")
        require(configuredTestDep.isTransitive == false)
        testDeps.single { it is ExternalDependency && it.group == "org.jetbrains.kotlin.jvm" && it.name == "org.jetbrains.kotlin.jvm.gradle.plugin" && it.version == "1.8.0" }
        testDeps.single { it is ExternalDependency && it.group == "org.jetbrains.kotlin.multiplatform" && it.name == "org.jetbrains.kotlin.multiplatform.gradle.plugin" }
    }
}
"""
        then:
        succeeds 'checkDeps'
    }

    def "understands project notations"() {
        when:
        settingsFile << "include 'otherProject'"

        buildFile <<  """
configurations {
    conf
    confTwo
}

project(':otherProject') {
    configurations {
        otherConf
    }
}

dependencies {
    conf project(':otherProject')
    confTwo project(path: ':otherProject', configuration: 'otherConf')
}

task checkDeps {
    doLast {
        def deps = configurations.conf.incoming.dependencies
        assert deps.size() == 1
        assert deps.find { it.dependencyProject.path == ':otherProject' && it.targetConfiguration == null }

        deps = configurations.confTwo.incoming.dependencies
        assert deps.size() == 1
        assert deps.find { it.dependencyProject.path == ':otherProject' && it.targetConfiguration == 'otherConf' }
    }
}
"""
        then:
        succeeds 'checkDeps'
    }

    def "understands client module notation with dependencies"() {
        when:
        buildFile <<  """
configurations {
    conf
}

dependencies {
    conf module('org.foo:moduleOne:1.0') {
        dependency 'org.foo:bar:1.0'
        dependencies ('org.foo:one:1', 'org.foo:two:1')
        dependency ('high:five:5') { transitive = false }
        dependency('org.test:lateversion') {
               version {
                  prefer '1.0'
                  strictly '1.1' // intentionally overriding "prefer"
               }
           }
    }
}

task checkDeps {
    doLast {
        def deps = configurations.conf.incoming.dependencies
        assert deps.size() == 1
        def dep = deps.find { it instanceof ClientModule && it.name == 'moduleOne' }
        assert dep
        assert dep.dependencies.size() == 5
        assert dep.dependencies.find { it.group == 'org.foo' && it.name == 'bar' && it.version == '1.0' && it.transitive == true }
        assert dep.dependencies.find { it.group == 'org.foo' && it.name == 'one' && it.version == '1' }
        assert dep.dependencies.find { it.group == 'org.foo' && it.name == 'two' && it.version == '1' }
        assert dep.dependencies.find { it.group == 'high' && it.name == 'five' && it.version == '5' && it.transitive == false }
        assert dep.dependencies.find { it.group == 'org.test' && it.name == 'lateversion' && it.version == '1.1' }
    }
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
    def "gradleApi dependency implements contentEquals"() {
        when:
        buildFile << """
            configurations {
              conf
            }

            dependencies {
              conf gradleApi()
            }

            task check {
                doLast {
                    assert dependencies.gradleApi().contentEquals(dependencies.gradleApi())
                    assert dependencies.gradleApi().is(dependencies.gradleApi())
                    assert dependencies.gradleApi() == dependencies.gradleApi()
                    assert configurations.conf.dependencies.contains(dependencies.gradleApi())
                }
            }
        """

        then:
        succeeds "check"
    }

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
}
