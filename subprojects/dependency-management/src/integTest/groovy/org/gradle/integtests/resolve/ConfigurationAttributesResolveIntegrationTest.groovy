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
                task checkDebug(dependsOn: configurations._compileFreeDebug) {
                    doLast {
                       assert configurations._compileFreeDebug.collect { it.name } == ['b-foo.jar']
                    }
                }
                task checkRelease(dependsOn: configurations._compileFreeRelease) {
                    doLast {
                       assert configurations._compileFreeRelease.collect { it.name } == ['b-bar.jar']
                    }
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
        notExecuted ':b:barJar'

        when:
        run ':a:checkRelease'

        then:
        executedAndNotSkipped ':b:barJar'
        notExecuted ':b:fooJar'
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
                task checkDebug(dependsOn: configurations._compileFreeDebug) {
                    doLast {
                        assert configurations._compileFreeDebug.collect { it.name } == ['b-foo.jar']
                    }
                }
                task checkRelease(dependsOn: configurations._compileFreeRelease) {
                    doLast {
                        assert configurations._compileFreeRelease.collect { it.name } == ['b-bar.jar']
                    }
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
        notExecuted ':b:barJar'

        when:
        run ':a:checkRelease'

        then:
        executedAndNotSkipped ':b:barJar'
        notExecuted ':b:fooJar'
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
                task checkDebug(dependsOn: configurations._compileFreeDebug) {
                    doLast {
                        assert configurations._compileFreeDebug.collect { it.name } == ['b-foo.jar']
                    }
                }
                task checkRelease(dependsOn: configurations._compileFreeRelease) {
                    doLast {
                        assert configurations._compileFreeRelease.collect { it.name } == ['b-bar.jar']
                    }
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
        notExecuted ':b:barJar'

        when:
        run ':a:checkRelease'

        then:
        executedAndNotSkipped ':b:barJar'
        notExecuted ':b:fooJar'
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
                task checkDebug(dependsOn: configurations._compileFreeDebug) {
                    doLast {
                        assert configurations._compileFreeDebug.collect { it.name } == ['b-bar.jar']
                    }
                }
                task checkRelease(dependsOn: configurations._compileFreeRelease) {
                    doLast {
                        assert configurations._compileFreeRelease.collect { it.name } == ['b-bar.jar']
                    }
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
        notExecuted ':b:fooJar'

        when:
        run ':a:checkRelease'

        then:
        executed ':b:barJar'
        notExecuted ':b:fooJar'
    }

    /**
     * Whenever a dependency on a project is found and that the client configuration
     * defines attributes, we try to find a target configuration with the same attributes
     * declared. However if no such configuration exists, what should we do?
     * This test implements option 1, which is falling back on the default configuration,
     * without error.
     *
     * Rationale: it mimics the current behavior of Gradle Android builds. It will cause
     * the build of artifacts which are not necessary for a specific task, but it won't fail
     * the build.
     */
    def "selects default configuration when no match is found"() {
        given:
        file('settings.gradle') << "include 'a', 'b'"
        buildFile << '''
            project(':a') {
                configurations {
                    _compileFreeDebug.attributes(buildType: 'debug', flavor: 'free')
                }
                dependencies {
                    _compileFreeDebug project(':b')
                }
                task checkDebug(dependsOn: configurations._compileFreeDebug) {
                    doLast {
                        assert configurations._compileFreeDebug.collect { it.name } == []
                    }
                }
            }
            project(':b') {
                configurations {
                    foo
                    bar
                    create 'default'
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
        notExecuted ':b:fooJar', ':b:barJar'

    }

    /**
     * Whenever a dependency on a project is found and that the client configuration
     * defines attributes, we try to find a target configuration with the same attributes
     * declared. However if 2 configurations on the target project declares the same attributes,
     * we don't know which one to choose.
     *
     * This test implements a first option, which is to make this an error case.
     */
    def "should fail with reasonable error message if more than one configuration matches the attributes"() {
        given:
        file('settings.gradle') << "include 'a', 'b'"
        buildFile << '''
            project(':a') {
                configurations {
                    compile.attributes(buildType: 'debug')
                }
                dependencies {
                    compile project(':b')
                }
                task check(dependsOn: configurations.compile)
            }
            project(':b') {
                configurations {
                    foo.attributes(buildType: 'debug')
                    bar.attributes(buildType: 'debug')
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
        fails ':a:check'

        then:
        failure.assertHasCause 'Cannot choose between the following configurations: [bar, foo]. All of then match the client attributes {buildType=debug}'
    }

    /**
     * If a configuration defines attributes, and that the target project declares configurations
     * with attributes too, should the attributes of parent configurations in the target project
     * be used?
     *
     * This test implements option 2, "no", which basically means that attributes of a configuration
     * are never inherited.
     *
     * Rationale: if we make configuration inherit attributes, then it means that 2 "child" configurations
     * could easily have the same set of attributes. It also means that just adding a configuration could
     * make resolution fail if we decide that 2 configurations with the same attributes lead to an error.
     *
     * Also, since configurations can have multiple parents, it would be very easy to face a situation
     * where ordering of the "extendsFrom" clauses trigger different resolution results.
     *
     * There's another reason for not allowing inheritance: it allows more precise selection, while still
     * allowing the build author/plugin writer to decide what attributes should be copied to child configurations.
     *
     */
    def "attributes of parent configurations should not be used when matching"() {
        given:
        file('settings.gradle') << "include 'a', 'b'"
        buildFile << '''
            project(':a') {
                configurations {
                    compile.attributes(p1: 'foo', p2: 'bar')
                }
                dependencies {
                    compile project(':b')
                }
                task check(dependsOn: configurations.compile)
            }
            project(':b') {
                configurations {
                    debug.attributes(p1: 'foo')
                    compile.extendsFrom debug
                    compile.attributes(p2: 'bar')
                }
                task fooJar(type: Jar) {
                   baseName = 'b-foo'
                }
                task barJar(type: Jar) {
                   baseName = 'b-bar'
                }
                artifacts {
                    debug fooJar
                    compile barJar
                }
            }

        '''

        when:
        fails ':a:check'

        then:
        failure.error.contains("default")

    }

    def "transitive dependencies of selected configuration are included"() {
        given:
        file('settings.gradle') << "include 'a', 'b', 'c', 'd'"
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
                task checkDebug(dependsOn: configurations._compileFreeDebug) {
                    doLast {
                       assert configurations._compileFreeDebug.collect { it.name } == ['b-foo.jar', 'c-transitive.jar']
                    }
                }
                task checkRelease(dependsOn: configurations._compileFreeRelease) {
                    doLast {
                       assert configurations._compileFreeRelease.collect { it.name } == ['b-bar.jar', 'd-transitive.jar']
                    }
                }
            }
            project(':b') {
                configurations {
                    foo.attributes(buildType: 'debug', flavor: 'free')
                    bar.attributes(buildType: 'release', flavor: 'free')
                }
                dependencies {
                    foo project(':c')
                    bar project(':d')
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
            project(':c') {
                configurations.create('default') {

                }
                artifacts {
                    'default' file('c-transitive.jar')
                }
            }
            project(':d') {
                configurations.create('default') {
                }
                artifacts {
                    'default' file('d-transitive.jar')
                }
            }

        '''

        when:
        run ':a:checkDebug'

        then:
        executedAndNotSkipped ':b:fooJar'
        notExecuted ':b:barJar'

        when:
        run ':a:checkRelease'

        then:
        executedAndNotSkipped ':b:barJar'
        notExecuted ':b:fooJar'
    }

}
