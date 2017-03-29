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
abstract class AbstractConfigurationAttributesResolveIntegrationTest extends AbstractIntegrationSpec {

    abstract String getTypeDefs()

    String getFreeDebug() {
        "${free}; ${debug}"
    }

    String getFreeRelease() {
        "${free}; ${release}"
    }

    abstract String getDebug()

    abstract String getFree()

    abstract String getRelease()

    abstract String getPaid()

    def "selects configuration in target project which matches the configuration attributes"() {
        given:
        file('settings.gradle') << "include 'a', 'b'"
        buildFile << """
            $typeDefs

            project(':a') {
                configurations {
                    _compileFreeDebug.attributes { $freeDebug }
                    _compileFreeRelease.attributes { $freeRelease }
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
                    foo {
                        attributes { $freeDebug }
                    }
                    bar {
                        attributes { $freeRelease }
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

        """

        when:
        run ':a:checkDebug'

        then:
        result.assertTasksExecuted(':b:fooJar', ':a:checkDebug')

        when:
        run ':a:checkRelease'

        then:
        result.assertTasksExecuted(':b:barJar', ':a:checkRelease')
    }

    def "selects configuration in target project which matches the configuration attributes when dependency is set on a parent configuration"() {
        given:
        file('settings.gradle') << "include 'a', 'b'"
        buildFile << """
            $typeDefs

            project(':a') {
                configurations {
                    compile
                    _compileFreeDebug.attributes { $freeDebug }
                    _compileFreeRelease.attributes { $freeRelease }
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
                    foo.attributes { $freeDebug }
                    bar.attributes { $freeRelease }
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

        """

        when:
        run ':a:checkDebug'

        then:
        result.assertTasksExecuted(':b:fooJar', ':a:checkDebug')

        when:
        run ':a:checkRelease'

        then:
        result.assertTasksExecuted(':b:barJar', ':a:checkRelease')
    }

    def "selects configuration in target project which matches the configuration attributes when dependency is set on a parent configuration and target configuration is not top-level"() {
        given:
        file('settings.gradle') << "include 'a', 'b'"
        buildFile << """
            $typeDefs

            project(':a') {
                configurations {
                    compile
                    _compileFreeDebug.attributes { $freeDebug }
                    _compileFreeRelease.attributes { $freeRelease }
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
                       attributes { $freeDebug }
                    }
                    bar {
                       extendsFrom compile
                       attributes { $freeRelease }
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

        """

        when:
        run ':a:checkDebug'

        then:
        result.assertTasksExecuted(':b:fooJar', ':a:checkDebug')

        when:
        run ':a:checkRelease'

        then:
        result.assertTasksExecuted(':b:barJar', ':a:checkRelease')
    }

    def "explicit configuration selection should take precedence"() {
        given:
        file('settings.gradle') << "include 'a', 'b'"
        buildFile << """
            $typeDefs

            project(':a') {
                configurations {
                    compile
                    _compileFreeDebug.attributes { $freeDebug }
                    _compileFreeRelease.attributes { $freeRelease }
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
                    freeDebug {
                       extendsFrom compile
                       attributes { $freeDebug }
                    }
                    freeRelease {
                       extendsFrom compile
                       attributes { $freeDebug }
                    }
                    bar {
                       extendsFrom compile
                       attributes { $freeDebug }
                    }
                }
                task fooJar(type: Jar) {
                   baseName = 'b-foo'
                }
                task barJar(type: Jar) {
                   baseName = 'b-bar'
                }
                artifacts {
                    freeDebug fooJar
                    freeRelease fooJar
                    bar barJar
                }
            }

        """

        when:
        run ':a:checkDebug'

        then:
        result.assertTasksExecuted(':b:barJar', ':a:checkDebug')
    }

    def "explicit configuration selection can be used when no configurations in target have attributes"() {
        given:
        file('settings.gradle') << "include 'a', 'b'"
        buildFile << """
            $typeDefs

            project(':a') {
                dependencies.attributesSchema {
                    attribute(buildType).compatibilityRules.assumeCompatibleWhenMissing()
                    attribute(flavor).compatibilityRules.assumeCompatibleWhenMissing()
                }
                configurations {
                    compile
                    _compileFreeDebug.attributes { $freeDebug }
                    _compileFreeRelease.attributes { $freeRelease }
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
            }
            project(':b') {
                configurations {
                    compile
                    foo {
                       extendsFrom compile
                    }
                    bar {
                       extendsFrom compile
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

        """

        when:
        run ':a:checkDebug'

        then:
        result.assertTasksExecuted(':b:barJar', ':a:checkDebug')
    }

    def "fails when explicitly selected configuration is not compatible with requested"() {
        given:
        file('settings.gradle') << "include 'a', 'b'"
        buildFile << """
            $typeDefs

            project(':a') {
                configurations {
                    compile
                    _compileFreeDebug.attributes { $freeDebug }
                    _compileFreeRelease.attributes { $freeRelease }
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
                       attributes { $freeDebug }
                    }
                    bar {
                       extendsFrom compile
                       attributes { $freeRelease }
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

        """

        when:
        fails ':a:checkDebug'

        then:
        failure.assertHasCause '''Configuration 'bar' in project :b does not match the consumer attributes
Configuration 'bar':
  - Required buildType 'debug' and found incompatible value 'release'.
  - Required flavor 'free' and found compatible value 'free'.'''

        when:
        run ':a:checkRelease'

        then:
        result.assertTasksExecuted(':b:barJar', ':a:checkRelease')
    }

    def "selects default configuration when it matches configuration attributes"() {
        given:
        file('settings.gradle') << "include 'a', 'b'"
        buildFile << """
            $typeDefs

            project(':a') {
                configurations {
                    _compileFreeDebug.attributes { $freeDebug }
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
                    create('default').attributes { $freeDebug }
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

        """

        when:
        run ':a:checkDebug'

        then:
        result.assertTasksExecuted(':a:checkDebug')
    }

    def "selects default configuration when target has no configurations with attributes"() {
        given:
        file('settings.gradle') << "include 'a', 'b'"
        buildFile << """
            $typeDefs

            project(':a') {
                configurations {
                    _compileFreeDebug.attributes { $freeDebug }
                }
                dependencies {
                    attributesSchema {
                        attribute(buildType).compatibilityRules.assumeCompatibleWhenMissing()
                        attribute(flavor).compatibilityRules.assumeCompatibleWhenMissing()
                    }
                    
                    _compileFreeDebug project(':b')
                }
                task checkDebug(dependsOn: configurations._compileFreeDebug) {
                    doLast {
                        assert configurations._compileFreeDebug.collect { it.name } == ['b-bar.jar']
                    }
                }
            }
            project(':b') {
                configurations {
                    foo
                    bar
                    create 'default'
                }
                task barJar(type: Jar) {
                   baseName = 'b-bar'
                }
                artifacts {
                    'default' barJar
                }
            }

        """

        when:
        run ':a:checkDebug'

        then:
        result.assertTasksExecuted(':b:barJar', ':a:checkDebug')
    }

    def "selects default configuration when no match is found"() {
        given:
        file('settings.gradle') << "include 'a', 'b'"
        buildFile << """
            $typeDefs

            project(':a') {
                configurations {
                    _compileFreeDebug.attributes { $freeDebug }
                }
                dependencies.attributesSchema {
                    attribute(buildType).compatibilityRules.assumeCompatibleWhenMissing()
                    attribute(flavor).compatibilityRules.assumeCompatibleWhenMissing()
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
                    foo { attributes { $freeRelease } }
                    bar { attributes { $release } }
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

        """

        when:
        run ':a:checkDebug'

        then:
        result.assertTasksExecuted(':a:checkDebug')
    }

    def "does not select default configuration when no match is found and default configuration is not consumable"() {
        given:
        file('settings.gradle') << "include 'a', 'b'"
        buildFile << """
            $typeDefs

            project(':a') {
                configurations {
                    _compileFreeDebug.attributes { $freeDebug }
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
                apply plugin: 'base'
                configurations {
                    foo
                    bar
                    'default' {
                        canBeConsumed = false
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

        """

        when:
        fails ':a:checkDebug'

        then:
        failure.assertHasCause "Unable to find a matching configuration in project :b: None of the consumable configurations have attributes."
    }

    def "does not select explicit configuration when it's not consumable"() {
        given:
        file('settings.gradle') << "include 'a', 'b'"
        buildFile << """
            $typeDefs

            project(':a') {
                configurations {
                    _compileFreeDebug.attributes { $freeDebug }
                }
                dependencies {
                    _compileFreeDebug project(path: ':b', configuration: 'someConf')
                }
                task checkDebug(dependsOn: configurations._compileFreeDebug) {
                    doLast {
                        assert configurations._compileFreeDebug.collect { it.name } == []
                    }
                }
            }
            project(':b') {
                apply plugin: 'base'
                configurations {
                    foo
                    bar
                    someConf {
                        canBeConsumed = false
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

        """

        when:
        fails ':a:checkDebug'

        then:
        failure.assertHasCause "Selected configuration 'someConf' on 'project :b' but it can't be used as a project dependency because it isn't intended for consumption by other components."

    }

    def "gives details about failing matches when it cannot select default configuration when no match is found and default configuration is not consumable"() {
        given:
        file('settings.gradle') << "include 'a', 'b'"
        buildFile << """
            $typeDefs

            project(':a') {
                configurations {
                    _compileFreeDebug.attributes { $freeDebug }
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
                apply plugin: 'base'
                configurations {
                    foo.attributes { $freeRelease }
                    bar.attributes { $paid; $release }
                    'default' {
                        canBeConsumed = false
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

        """

        when:
        fails ':a:checkDebug'

        then:
        failure.assertHasCause '''Unable to find a matching configuration in project :b:
  - Configuration 'bar':
      - Required buildType 'debug' and found incompatible value 'release'.
      - Required flavor 'free' and found incompatible value 'paid'.
  - Configuration 'foo':
      - Required buildType 'debug' and found incompatible value 'release'.
      - Required flavor 'free' and found compatible value 'free'.'''

    }

    def "chooses a configuration when partial match is found"() {
        given:
        file('settings.gradle') << "include 'a', 'b'"
        buildFile << """
            $typeDefs

            project(':a') {                
                configurations {
                    _compileFreeDebug.attributes { $freeDebug }
                }
                dependencies {
                    attributesSchema {
                        attribute(flavor) {
                            compatibilityRules.assumeCompatibleWhenMissing()
                        }
                    }
                    _compileFreeDebug project(':b')
                }
                task checkDebug(dependsOn: configurations._compileFreeDebug) {
                    doLast {
                        assert configurations._compileFreeDebug.collect { it.name } == ['b-foo.jar']
                    }
                }
            }
            project(':b') {
                configurations {
                    create 'default'
                    foo {
                       attributes { $debug } // partial match on `buildType`
                    }
                    bar {
                       attributes { $paid } // no match on `flavor`
                    }
                }
                task defaultJar(type: Jar) {
                   baseName = 'b-default'
                }
                task fooJar(type: Jar) {
                   baseName = 'b-foo'
                }
                task barJar(type: Jar) {
                   baseName = 'b-bar'
                }
                artifacts {
                    'default' defaultJar
                    foo fooJar
                    bar barJar
                }
            }

        """

        when:
        run ':a:checkDebug'

        then:
        result.assertTasksExecuted(':b:fooJar', ':a:checkDebug')
    }

    def "cannot choose a configuration when multiple partial matches are found"() {
        given:
        file('settings.gradle') << "include 'a', 'b'"
        buildFile << """
            $typeDefs

            project(':a') {
                configurations {
                    _compileFreeDebug.attributes { $freeDebug }
                }
                dependencies {
                    attributesSchema {
                        attribute(flavor) {
                            compatibilityRules.assumeCompatibleWhenMissing()
                        }
                        attribute(buildType) {
                            compatibilityRules.assumeCompatibleWhenMissing()
                        }
                    }
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
                    create 'default'
                    foo {
                       attributes { $debug } // partial match on `buildType`
                    }
                    bar {
                       attributes { $free } // partial match on `flavor`
                    }
                }
                task defaultJar(type: Jar) {
                   baseName = 'b-default'
                }
                task fooJar(type: Jar) {
                   baseName = 'b-foo'
                }
                task barJar(type: Jar) {
                   baseName = 'b-bar'
                }
                artifacts {
                    'default' defaultJar
                    foo fooJar
                    bar barJar
                }
            }

        """

        when:
        fails ':a:checkDebug'

        then:
        failure.assertHasCause("""Cannot choose between the following configurations on project :b:
  - bar
  - foo
All of them match the consumer attributes:
  - Configuration 'bar':
      - Required buildType 'debug' but no value provided.
      - Required flavor 'free' and found compatible value 'free'.
  - Configuration 'foo':
      - Required buildType 'debug' and found compatible value 'debug'.
      - Required flavor 'free' but no value provided.""")
    }

    def "selects configuration when it has more attributes than the resolved configuration"() {
        given:
        file('settings.gradle') << "include 'a', 'b'"
        buildFile << """
            $typeDefs

            project(':a') {
                configurations {
                    _compileFreeDebug.attributes { $freeDebug }
                }
                dependencies {
                    _compileFreeDebug project(':b')
                }
                task checkDebug(dependsOn: configurations._compileFreeDebug) {
                    doLast {
                        assert configurations._compileFreeDebug.collect { it.name } == ['b-foo.jar']
                    }
                }
            }
            project(':b') {
                dependencies {
                    attributesSchema {
                        attribute(extra) {
                            compatibilityRules.assumeCompatibleWhenMissing()
                        }
                    }
                }
                configurations {
                    foo {
                       attributes { $freeDebug; attribute(extra, 'extra') }
                    }
                    bar {
                       attributes { $free }
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

        """

        when:
        run ':a:checkDebug'

        then:
        result.assertTasksExecuted(':b:fooJar', ':a:checkDebug')
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
        buildFile << """
            $typeDefs

            project(':a') {
                configurations {
                    compile.attributes { $debug }
                }
                dependencies {
                    compile project(':b')
                }
                task check(dependsOn: configurations.compile)
            }
            project(':b') {
                configurations {
                    foo.attributes { $debug }
                    bar.attributes { $debug }
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

        """

        when:
        fails ':a:check'

        then:
        failure.assertHasCause """Cannot choose between the following configurations on project :b:
  - bar
  - foo
All of them match the consumer attributes:
  - Configuration 'bar': Required buildType 'debug' and found compatible value 'debug'.
  - Configuration 'foo': Required buildType 'debug' and found compatible value 'debug'."""
    }

    def "fails when multiple configurations match but have more attributes than requested"() {
        given:
        file('settings.gradle') << "include 'a', 'b'"
        buildFile << """
            $typeDefs

            project(':a') {
                configurations {
                    _compileFreeDebug.attributes { $freeDebug }
                }
                dependencies {
                    _compileFreeDebug project(':b')
                }
                task checkDebug(dependsOn: configurations._compileFreeDebug) {
                    doLast {
                        assert configurations._compileFreeDebug.collect { it.name } == ['b-foo.jar']
                    }
                }
            }
            project(':b') {
                dependencies {
                    attributesSchema {
                        attribute(extra) {
                            compatibilityRules.assumeCompatibleWhenMissing()
                        }
                    }
                }
                configurations {
                    foo {
                       attributes { $freeDebug; attribute(extra, 'extra') }
                    }
                    bar {
                      attributes { $freeDebug; attribute(extra, 'extra 2') }
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

        """

        when:
        fails ':a:checkDebug'

        then:
        failure.assertHasCause """Cannot choose between the following configurations on project :b:
  - bar
  - foo
All of them match the consumer attributes:
  - Configuration 'bar':
      - Required buildType 'debug' and found compatible value 'debug'.
      - Found extra 'extra 2' but wasn't required.
      - Required flavor 'free' and found compatible value 'free'.
  - Configuration 'foo':
      - Required buildType 'debug' and found compatible value 'debug'.
      - Found extra 'extra' but wasn't required.
      - Required flavor 'free' and found compatible value 'free'."""
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
        buildFile << """
            $typeDefs

            project(':a') {
                dependencies { 
                    attributesSchema {
                        attribute(flavor) {
                            compatibilityRules.assumeCompatibleWhenMissing()
                        }
                        attribute(buildType) {
                            compatibilityRules.assumeCompatibleWhenMissing()
                        }
                    }
                }
                configurations {
                    compile.attributes { $freeDebug }
                }
                dependencies {
                    compile project(':b')
                }
                task check(dependsOn: configurations.compile)
            }
            project(':b') {
                configurations {
                    debug.attributes { $debug }
                    compile.extendsFrom debug
                    compile.attributes { $free }
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

        """

        when:
        fails ':a:check'

        then:
        failure.assertHasCause """Cannot choose between the following configurations on project :b:
  - compile
  - debug
All of them match the consumer attributes:
  - Configuration 'compile':
      - Required buildType 'debug' but no value provided.
      - Required flavor 'free' and found compatible value 'free'.
  - Configuration 'debug':
      - Required buildType 'debug' and found compatible value 'debug'.
      - Required flavor 'free' but no value provided."""
    }

    def "transitive dependencies of selected configuration are included"() {
        given:
        file('settings.gradle') << "include 'a', 'b', 'c', 'd'"
        buildFile << """
            $typeDefs

            allprojects {
               dependencies {
                   attributesSchema {
                      attribute(flavor).compatibilityRules.assumeCompatibleWhenMissing()
                      attribute(buildType).compatibilityRules.assumeCompatibleWhenMissing()
                   }
               }
            }

            project(':a') {
                configurations {
                    _compileFreeDebug.attributes { $freeDebug }
                    _compileFreeRelease.attributes { $freeRelease }
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
                    foo.attributes { $freeDebug }
                    bar.attributes { $freeRelease }
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

        """

        when:
        run ':a:checkDebug'

        then:
        result.assertTasksExecuted(':b:fooJar', ':a:checkDebug')

        when:
        run ':a:checkRelease'

        then:
        result.assertTasksExecuted(':b:barJar', ':a:checkRelease')
    }

    def "context travels down to transitive dependencies"() {
        given:
        file('settings.gradle') << "include 'a', 'b', 'c'"
        buildFile << """
            $typeDefs

            allprojects {
               dependencies {
                   attributesSchema {
                      attribute(flavor).compatibilityRules.assumeCompatibleWhenMissing()
                      attribute(buildType).compatibilityRules.assumeCompatibleWhenMissing()
                   }
               }
            }

            project(':a') {
                configurations {
                    _compileFreeDebug.attributes { $freeDebug }
                    _compileFreeRelease.attributes { $freeRelease }
                }
                dependencies {
                    _compileFreeDebug project(':b')
                    _compileFreeRelease project(':b')
                }
                task checkDebug(dependsOn: configurations._compileFreeDebug) {
                    doLast {
                       assert configurations._compileFreeDebug.collect { it.name } == ['b-transitive.jar', 'c-foo.jar']
                    }
                }
                task checkRelease(dependsOn: configurations._compileFreeRelease) {
                    doLast {
                       assert configurations._compileFreeRelease.collect { it.name } == ['b-transitive.jar', 'c-bar.jar']
                    }
                }
            }
            project(':b') {
                configurations.create('default') {

                }
                artifacts {
                    'default' file('b-transitive.jar')
                }
                dependencies {
                    'default' project(':c')
                }
            }
            project(':c') {
                configurations {
                    foo.attributes { $freeDebug }
                    bar.attributes { $freeRelease }
                }
                task fooJar(type: Jar) {
                   baseName = 'c-foo'
                }
                task barJar(type: Jar) {
                   baseName = 'c-bar'
                }
                artifacts {
                    foo fooJar
                    bar barJar
                }
            }

        """

        when:
        run ':a:checkDebug'

        then:
        result.assertTasksExecuted(':c:fooJar', ':a:checkDebug')

        when:
        run ':a:checkRelease'

        then:
        result.assertTasksExecuted(':c:barJar', ':a:checkRelease')
    }

    def "context travels down to transitive dependencies with dependency substitution"() {
        given:
        file('settings.gradle') << "include 'a', 'b', 'c'"
        buildFile << """
            $typeDefs

            allprojects {
               dependencies {
                   attributesSchema {
                      attribute(flavor).compatibilityRules.assumeCompatibleWhenMissing()
                      attribute(buildType).compatibilityRules.assumeCompatibleWhenMissing()
                   }
               }
            }

            project(':a') {
                configurations {
                    _compileFreeDebug.attributes { $freeDebug }
                    _compileFreeRelease.attributes { $freeRelease }
                }
                dependencies {
                    _compileFreeDebug project(':b')
                    _compileFreeRelease project(':b')
                }
                task checkDebug(dependsOn: configurations._compileFreeDebug) {
                    doLast {
                       assert configurations._compileFreeDebug.collect { it.name } == ['b-transitive.jar', 'c-foo.jar']
                    }
                }
                task checkRelease(dependsOn: configurations._compileFreeRelease) {
                    doLast {
                       assert configurations._compileFreeRelease.collect { it.name } == ['b-transitive.jar', 'c-bar.jar']
                    }
                }
                configurations.all {
                    resolutionStrategy.dependencySubstitution {
                        substitute module('com.acme.external:external') with project(":c")
                    }
                }
            }
            project(':b') {
                configurations.create('default') {

                }
                artifacts {
                    'default' file('b-transitive.jar')
                }
                dependencies {
                    'default'('com.acme.external:external:1.0')
                }
            }
            project(':c') {
                configurations {
                    foo.attributes { $freeDebug }
                    bar.attributes { $freeRelease }
                }
                task fooJar(type: Jar) {
                   baseName = 'c-foo'
                }
                task barJar(type: Jar) {
                   baseName = 'c-bar'
                }
                artifacts {
                    foo fooJar
                    bar barJar
                }
            }

        """

        when:
        run ':a:checkDebug'

        then:
        result.assertTasksExecuted(':c:fooJar', ':a:checkDebug')

        when:
        run ':a:checkRelease'

        then:
        result.assertTasksExecuted(':c:barJar', ':a:checkRelease')
    }

    def "transitive dependencies selection uses the source configuration attributes"() {
        given:
        file('settings.gradle') << "include 'a', 'b', 'c'"
        buildFile << """
            $typeDefs
            allprojects {
                dependencies { 
                    attributesSchema {
                        attribute(extra) {
                            compatibilityRules.assumeCompatibleWhenMissing()
                        }
                    }
                }
            }
            project(':a') {
                configurations {
                    _compileFreeDebug.attributes { $freeDebug }
                    _compileFreeRelease.attributes { $freeRelease }
                }
                dependencies {
                    _compileFreeDebug project(':b')
                    _compileFreeRelease project(':b')
                }
                task checkDebug(dependsOn: configurations._compileFreeDebug) {
                    doLast {
                       assert configurations._compileFreeDebug.collect { it.name } == ['b-foo.jar', 'c-foo.jar']
                    }
                }
                task checkRelease(dependsOn: configurations._compileFreeRelease) {
                    doLast {
                       assert configurations._compileFreeRelease.collect { it.name } == ['b-bar.jar', , 'c-bar.jar']
                    }
                }
            }
            project(':b') {
                configurations {
                    foo.attributes { $freeDebug; attribute(extra, 'extra') } // the "extra" attribute will be used when matching ':c'
                    bar.attributes { $freeRelease; attribute(extra, 'extra') } // the "extra" attribute will be used when matching ':c'
                }
                dependencies {
                    foo project(':c')
                    bar project(':c')
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
                configurations {
                    foo.attributes { $freeDebug; attribute(extra, 'extra') }
                    foo2.attributes { $freeDebug; attribute(extra, 'extra 2') }
                    bar.attributes { $freeRelease; attribute(extra, 'extra') }
                    bar2.attributes { $freeRelease; attribute(extra, 'extra 2') }
                }
                task fooJar(type: Jar) {
                   baseName = 'c-foo'
                }
                task barJar(type: Jar) {
                   baseName = 'c-bar'
                }
                task foo2Jar(type: Jar) {
                   baseName = 'c-foo2'
                }
                task bar2Jar(type: Jar) {
                   baseName = 'c-bar2'
                }
                artifacts {
                    foo fooJar, foo2Jar
                    bar barJar, bar2Jar
                }
            }

        """

        when:
        fails ':a:checkDebug'

        then:
        failure.assertHasCause """Cannot choose between the following configurations on project :c:
  - foo
  - foo2
All of them match the consumer attributes:
  - Configuration 'foo':
      - Required buildType 'debug' and found compatible value 'debug'.
      - Found extra 'extra' but wasn't required.
      - Required flavor 'free' and found compatible value 'free'.
  - Configuration 'foo2':
      - Required buildType 'debug' and found compatible value 'debug'.
      - Found extra 'extra 2' but wasn't required.
      - Required flavor 'free' and found compatible value 'free'."""

        when:
        fails ':a:checkRelease'

        then:
        failure.assertHasCause """Cannot choose between the following configurations on project :c:
  - bar
  - bar2
All of them match the consumer attributes:
  - Configuration 'bar':
      - Required buildType 'release' and found compatible value 'release'.
      - Found extra 'extra' but wasn't required.
      - Required flavor 'free' and found compatible value 'free'.
  - Configuration 'bar2':
      - Required buildType 'release' and found compatible value 'release'.
      - Found extra 'extra 2' but wasn't required.
      - Required flavor 'free' and found compatible value 'free'."""

    }

    def "context travels down to transitive dependencies with external dependencies in graph"() {
        given:
        file('settings.gradle') << "include 'a', 'b', 'c'"
        buildFile << """
            $typeDefs

            allprojects {
               dependencies {
                   attributesSchema {
                      attribute(flavor).compatibilityRules.assumeCompatibleWhenMissing()
                      attribute(buildType).compatibilityRules.assumeCompatibleWhenMissing()
                   }
               }
            }

            project(':a') {
                repositories { jcenter() }

                configurations {
                    _compileFreeDebug.attributes { $freeDebug }
                    _compileFreeRelease.attributes { $freeRelease }
                }
                dependencies {
                    _compileFreeDebug project(':b')
                    _compileFreeRelease project(':b')
                    _compileFreeDebug 'org.apache.commons:commons-lang3:3.5'
                }
                task checkDebug(dependsOn: configurations._compileFreeDebug) {
                    doLast {
                       assert configurations._compileFreeDebug.collect { it.name }.sort { it } == ['b-transitive.jar', 'c-foo.jar', 'commons-lang3-3.5.jar']
                    }
                }
                task checkRelease(dependsOn: configurations._compileFreeRelease) {
                    doLast {
                       assert configurations._compileFreeRelease.collect { it.name }.sort { it } == ['b-transitive.jar', 'c-bar.jar', 'commons-lang3-3.4.jar']
                    }
                }
            }
            project(':b') {
                configurations.create('default') {

                }
                artifacts {
                    'default' file('b-transitive.jar')
                }
                dependencies {
                    'default' project(':c')
                }
            }
            project(':c') {
                repositories { jcenter() }
                configurations {
                    foo.attributes { $freeDebug }
                    bar.attributes { $freeRelease }
                }
                dependencies {
                    bar 'org.apache.commons:commons-lang3:3.4'
                }
                task fooJar(type: Jar) {
                   baseName = 'c-foo'
                }
                task barJar(type: Jar) {
                   baseName = 'c-bar'
                }
                artifacts {
                    foo fooJar
                    bar barJar
                }
            }

        """

        when:
        run ':a:checkDebug'

        then:
        result.assertTasksExecuted(':c:fooJar', ':a:checkDebug')

        when:
        run ':a:checkRelease'

        then:
        result.assertTasksExecuted(':c:barJar', ':a:checkRelease')
    }

    def "two configurations can have the same attributes but for different roles"() {
        given:
        file('settings.gradle') << "include 'a', 'b'"
        buildFile << """
            $typeDefs

            project(':a') {
                configurations {
                    compileFreeDebug.attributes { $freeDebug }
                    compileFreeRelease.attributes { $freeRelease }
                    compileFreeDebug.canBeConsumed = false
                    compileFreeRelease.canBeConsumed = false
                }
                dependencies {
                    compileFreeDebug project(':b')
                    compileFreeRelease project(':b')
                }
                task checkDebug(dependsOn: configurations.compileFreeDebug) {
                    doLast {
                       assert configurations.compileFreeDebug.collect { it.name } == ['b-foo.jar']
                    }
                }
                task checkRelease(dependsOn: configurations.compileFreeRelease) {
                    doLast {
                       assert configurations.compileFreeRelease.collect { it.name } == ['b-bar.jar']
                    }
                }
            }
            project(':b') {
                configurations {
                    // configurations used when resolving
                    compileFreeDebug.attributes { $freeDebug }
                    compileFreeRelease.attributes { $freeRelease }
                    compileFreeDebug.canBeConsumed = false
                    compileFreeRelease.canBeConsumed = false
                    // configurations used when selecting dependencies
                    _compileFreeDebug {
                        attributes { $freeDebug }
                    }
                    _compileFreeRelease {
                        attributes { $freeRelease }
                    }
                }
                task fooJar(type: Jar) {
                   baseName = 'b-foo'
                }
                task barJar(type: Jar) {
                   baseName = 'b-bar'
                }
                artifacts {
                    _compileFreeDebug fooJar
                    _compileFreeRelease barJar
                }
            }

        """

        when:
        run ':a:checkDebug'

        then:
        result.assertTasksExecuted(':b:fooJar', ':a:checkDebug')

        when:
        run ':a:checkRelease'

        then:
        result.assertTasksExecuted(':b:barJar', ':a:checkRelease')
    }

    def "Library project with flavors depends on a library project that does not"() {
        given:
        file('settings.gradle') << "include 'a', 'b'"
        buildFile << """
            $typeDefs

            project(':a') {
                configurations {
                    _compileFreeDebug.attributes { $freeDebug }
                }
                dependencies {
                    attributesSchema {
                        attribute(flavor) {
                            compatibilityRules.assumeCompatibleWhenMissing()
                        }
                    }
                    _compileFreeDebug project(':b')
                }
                task checkDebug(dependsOn: configurations._compileFreeDebug) {
                    doLast {
                        assert configurations._compileFreeDebug.collect { it.name } == ['b-foo.jar']
                    }
                }
            }
            project(':b') {
                configurations {
                    foo {
                       attributes { $debug } // partial match on `buildType`
                    }
                    bar {
                       attributes { $release } // no match on `buildType`
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
        """

        when:
        run ':a:checkDebug'

        then:
        result.assertTasksExecuted(':b:fooJar', ':a:checkDebug')
    }

    def "Library project without flavors depends on a library project with flavors"() {
        given:
        file('settings.gradle') << "include 'a', 'b'"
        buildFile << """
            $typeDefs
            project(':a') {
                configurations {
                    _compileFreeDebug.attributes { $debug }
                }
                dependencies {
                    _compileFreeDebug project(':b')
                }
                task checkDebug(dependsOn: configurations._compileFreeDebug) {
                    doLast {
                        assert configurations._compileFreeDebug.collect { it.name } == ['b-foo.jar']
                    }
                }
            }
            project(':b') {
                dependencies {
                    attributesSchema {
                        attribute(flavor) {
                            compatibilityRules.assumeCompatibleWhenMissing()
                        }
                    }
                }
                configurations {
                    foo {
                       attributes { $freeDebug } // match on `buildType`
                    }
                    bar {
                       attributes { $freeRelease } // match on `buildType`
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
        """

        when:
        run ':a:checkDebug'

        then:
        result.assertTasksExecuted(':b:fooJar', ':a:checkDebug')
    }

    def "Library project with flavors depends on library project that does not which depends on library project with flavors"() {
        given:
        file('settings.gradle') << "include 'a', 'b', 'c'"
        buildFile << """
            $typeDefs

            allprojects {
                dependencies {
                    attributesSchema {
                        attribute(flavor) {
                            compatibilityRules.assumeCompatibleWhenMissing()
                        }
                        attribute(buildType) {
                            compatibilityRules.assumeCompatibleWhenMissing()
                        }
                    }
                }
            }

            project(':a') {
                configurations {
                    _compileFreeDebug.attributes { $freeDebug }
                }
                dependencies {
                    _compileFreeDebug project(':b')
                }
                task checkDebug(dependsOn: configurations._compileFreeDebug) {
                    doLast {
                        assert configurations._compileFreeDebug.collect { it.name } == ['b-foo.jar', 'c-foo.jar']
                    }
                }
            }
            project(':b') {
                configurations {
                    foo {
                       attributes { $debug } // partial match on `buildType`
                    }
                    bar {
                       attributes { $release } // no match on `buildType`
                    }
                }
                dependencies {
                    foo project(':c')
                    bar project(':c')
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
                configurations {
                    foo {
                       attributes { $freeDebug } // exact match on `buildType` and `flavor`
                    }
                    bar {
                       attributes { ${debug}; ${paid} } // partial match on `buildType`
                    }
                }
                task fooJar(type: Jar) {
                   baseName = 'c-foo'
                }
                task barJar(type: Jar) {
                   baseName = 'c-bar'
                }
                artifacts {
                    foo fooJar
                    bar barJar
                }
            }
        """

        when:
        run ':a:checkDebug'

        then:
        result.assertTasksExecuted(':b:fooJar', ':c:fooJar', ':a:checkDebug')
    }

    def "selects configuration with superset of matching attributes"() {
        given:
        file('settings.gradle') << "include 'a', 'b'"
        buildFile << """
            $typeDefs

            project(':a') {
                configurations {
                    _compileFreeDebug.attributes { $freeDebug; attribute(extra, 'EXTRA') }
                }
                dependencies {
                    attributesSchema {
                        attribute(extra).compatibilityRules.assumeCompatibleWhenMissing()
                        attribute(buildType).compatibilityRules.assumeCompatibleWhenMissing()
                        attribute(flavor).compatibilityRules.assumeCompatibleWhenMissing()
                    }
                    _compileFreeDebug project(':b')
                }
                task checkDebug(dependsOn: configurations._compileFreeDebug) {
                    doLast {
                        assert configurations._compileFreeDebug.collect { it.name } == ['b-foo.jar']
                    }
                }
            }
            project(':b') {
                configurations {
                    create 'default'
                    foo {
                       attributes { $free; $debug }
                    }
                    bar {
                       attributes { $free }
                    }
                }
                task defaultJar(type: Jar) {
                   baseName = 'b-default'
                }
                task fooJar(type: Jar) {
                   baseName = 'b-foo'
                }
                task barJar(type: Jar) {
                   baseName = 'b-bar'
                }
                artifacts {
                    'default' defaultJar
                    foo fooJar
                    bar barJar
                }
            }

        """

        when:
        run ':a:checkDebug'

        then:
        result.assertTasksExecuted(':b:fooJar', ':a:checkDebug')
    }

}
