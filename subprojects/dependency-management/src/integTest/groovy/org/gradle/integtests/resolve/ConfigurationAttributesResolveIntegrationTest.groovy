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
package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.FluidDependenciesResolveRunner
import org.junit.runner.RunWith

@RunWith(FluidDependenciesResolveRunner)
class ConfigurationAttributesResolveIntegrationTest extends AbstractIntegrationSpec {

    def "selects configuration in target project which matches the configuration attributes"() {
        given:
        file('settings.gradle') << "include 'a', 'b'"
        buildFile << '''
            project(':a') {
                configurations {
                    _compileFreeDebug.attributes(buildType: 'debug', flavor: 'free')
                    _compileFreeRelease.attributes(buildType: 'release', flavor: 'free')
                }
                dependencies {
                    _compileFreeDebug project(':b')
                    _compileFreeRelease project(':b')
                }
                task checkDebug(dependsOn: configurations._compileFreeDebug) << {
                    assert configurations._compileFreeDebug.collect { it.name } == ['b-foo.jar']
                }
                task checkRelease(dependsOn: configurations._compileFreeRelease) << {
                    assert configurations._compileFreeRelease.collect { it.name } == ['b-bar.jar']
                }
            }
            project(':b') {
                configurations {
                    foo.attributes(buildType: 'debug', flavor: 'free')
                    bar.attributes(buildType: 'release', flavor: 'free')
                }
                task fooJar(type: Jar) {
                   baseName = 'b-foo'
                }
                task barJar(type: Jar) {
                   baseName = 'b-bar'
                }
                artifacts {
                    foo fooJar
                    bar barJar
                }
            }

        '''

        when:
        run ':a:checkDebug'

        then:
        executedAndNotSkipped ':b:fooJar'

        when:
        run ':a:checkRelease'

        then:
        executedAndNotSkipped ':b:barJar'
    }

    def "selects configuration in target project which matches the configuration attributes when dependency is set on a parent configuration"() {
        given:
        file('settings.gradle') << "include 'a', 'b'"
        buildFile << '''
            project(':a') {
                configurations {
                    compile
                    _compileFreeDebug.attributes(buildType: 'debug', flavor: 'free')
                    _compileFreeRelease.attributes(buildType: 'release', flavor: 'free')
                    _compileFreeDebug.extendsFrom compile
                    _compileFreeRelease.extendsFrom compile
                }
                dependencies {
                    compile project(':b')
                }
                task checkDebug(dependsOn: configurations._compileFreeDebug) << {
                    assert configurations._compileFreeDebug.collect { it.name } == ['b-foo.jar']
                }
                task checkRelease(dependsOn: configurations._compileFreeRelease) << {
                    assert configurations._compileFreeRelease.collect { it.name } == ['b-bar.jar']
                }
            }
            project(':b') {
                configurations {
                    foo.attributes(buildType: 'debug', flavor: 'free')
                    bar.attributes(buildType: 'release', flavor: 'free')
                }
                task fooJar(type: Jar) {
                   baseName = 'b-foo'
                }
                task barJar(type: Jar) {
                   baseName = 'b-bar'
                }
                artifacts {
                    foo fooJar
                    bar barJar
                }
            }

        '''

        when:
        run ':a:checkDebug'

        then:
        executedAndNotSkipped ':b:fooJar'

        when:
        run ':a:checkRelease'

        then:
        executedAndNotSkipped ':b:barJar'
    }

    def "selects configuration in target project which matches the configuration attributes when dependency is set on a parent configuration and target configuration is not top-level"() {
        given:
        file('settings.gradle') << "include 'a', 'b'"
        buildFile << '''
            project(':a') {
                configurations {
                    compile
                    _compileFreeDebug.attributes(buildType: 'debug', flavor: 'free')
                    _compileFreeRelease.attributes(buildType: 'release', flavor: 'free')
                    _compileFreeDebug.extendsFrom compile
                    _compileFreeRelease.extendsFrom compile
                }
                dependencies {
                    compile project(':b')
                }
                task checkDebug(dependsOn: configurations._compileFreeDebug) << {
                    assert configurations._compileFreeDebug.collect { it.name } == ['b-foo.jar']
                }
                task checkRelease(dependsOn: configurations._compileFreeRelease) << {
                    assert configurations._compileFreeRelease.collect { it.name } == ['b-bar.jar']
                }
            }
            project(':b') {
                configurations {
                    compile
                    foo {
                       extendsFrom compile
                       attributes(buildType: 'debug', flavor: 'free')
                    }
                    bar {
                       extendsFrom compile
                       attributes(buildType: 'release', flavor: 'free')
                    }
                }
                task fooJar(type: Jar) {
                   baseName = 'b-foo'
                }
                task barJar(type: Jar) {
                   baseName = 'b-bar'
                }
                artifacts {
                    foo fooJar
                    bar barJar
                }
            }

        '''

        when:
        run ':a:checkDebug'

        then:
        executedAndNotSkipped ':b:fooJar'

        when:
        run ':a:checkRelease'

        then:
        executedAndNotSkipped ':b:barJar'
    }

    def "explicit configuration selection should take precedence"() {
        given:
        file('settings.gradle') << "include 'a', 'b'"
        buildFile << '''
            project(':a') {
                configurations {
                    compile
                    _compileFreeDebug.attributes(buildType: 'debug', flavor: 'free')
                    _compileFreeRelease.attributes(buildType: 'release', flavor: 'free')
                    _compileFreeDebug.extendsFrom compile
                    _compileFreeRelease.extendsFrom compile
                }
                dependencies {
                    compile project(path:':b', configuration: 'bar')
                }
                task checkDebug(dependsOn: configurations._compileFreeDebug) << {
                    assert configurations._compileFreeDebug.collect { it.name } == ['b-bar.jar']
                }
                task checkRelease(dependsOn: configurations._compileFreeRelease) << {
                    assert configurations._compileFreeRelease.collect { it.name } == ['b-bar.jar']
                }
            }
            project(':b') {
                configurations {
                    compile
                    foo {
                       extendsFrom compile
                       attributes(buildType: 'debug', flavor: 'free')
                    }
                    bar {
                       extendsFrom compile
                       attributes(buildType: 'release', flavor: 'free')
                    }
                }
                task fooJar(type: Jar) {
                   baseName = 'b-foo'
                }
                task barJar(type: Jar) {
                   baseName = 'b-bar'
                }
                artifacts {
                    foo fooJar
                    bar barJar
                }
            }

        '''

        when:
        run ':a:checkDebug'

        then:
        executedAndNotSkipped ':b:barJar'

        when:
        run ':a:checkRelease'

        then:
        executed ':b:barJar'
    }

}
