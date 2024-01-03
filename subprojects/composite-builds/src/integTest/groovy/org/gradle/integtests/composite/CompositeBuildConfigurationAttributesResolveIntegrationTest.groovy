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

package org.gradle.integtests.composite

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture

class CompositeBuildConfigurationAttributesResolveIntegrationTest extends AbstractIntegrationSpec {
    def resolve = new ResolveTestFixture(buildFile)

    def setup() {
        settingsFile << """
            rootProject.name = 'test'
        """
        using m2
    }

    def "context travels to transitive dependencies"() {
        given:
        createDirs("a", "b", "includedBuild")
        file('settings.gradle') << """
            include 'a', 'b'
            includeBuild 'includedBuild'
        """
        buildFile << '''
            def buildType = Attribute.of('buildType', String)
            def flavor = Attribute.of('flavor', String)
            allprojects {
                dependencies {
                    attributesSchema {
                        attribute(buildType)
                        attribute(flavor)
                    }
                }
            }
            project(':a') {
                configurations {
                    _compileFreeDebug.attributes { attribute(buildType, 'debug'); attribute(flavor, 'free') }
                    _compileFreeRelease.attributes { attribute(buildType, 'release'); attribute(flavor, 'free') }
                }
                dependencies {
                    _compileFreeDebug project(':b')
                    _compileFreeRelease project(':b')
                }
            }
            project(':b') {
                configurations.create('default')
                artifacts {
                    'default' file('b-transitive.jar')
                }
                dependencies {
                    'default'('com.acme.external:external:1.0')
                }
            }
        '''
        resolve.prepare {
            config('_compileFreeDebug', 'checkDebug')
            config('_compileFreeRelease', 'checkRelease')
        }

        file('includedBuild/build.gradle') << """

            group = 'com.acme.external'
            version = '2.0-SNAPSHOT'

            def buildType = Attribute.of('buildType', String)
            def flavor = Attribute.of('flavor', String)
            dependencies {
                attributesSchema {
                    attribute(buildType)
                    attribute(flavor)
                }
            }

            configurations {
                foo.attributes { attribute(buildType, 'debug'); attribute(flavor, 'free') }
                bar.attributes { attribute(buildType, 'release'); attribute(flavor, 'free') }
            }

            ${fooAndBarJars()}
        """
        file('includedBuild/settings.gradle') << '''
            rootProject.name = 'external'
        '''

        when:
        run ':a:checkDebug'

        then:
        executedAndNotSkipped ':includedBuild:fooJar'
        notExecuted ':includedBuild:barJar'
        resolve.expectGraph {
            root(':a', 'test:a:') {
                project(':b', 'test:b:') {
                    artifact(name: 'b-transitive')
                    edge('com.acme.external:external:1.0', ':includedBuild', 'com.acme.external:external:2.0-SNAPSHOT') {
                        compositeSubstitute()
                        artifact(name: 'c-foo', fileName: 'c-foo.jar')
                    }
                }
            }
        }

        when:
        run ':a:checkRelease'

        then:
        executedAndNotSkipped ':includedBuild:barJar'
        notExecuted ':includedBuild:fooJar'
        resolve.expectGraph {
            root(':a', 'test:a:') {
                project(':b', 'test:b:') {
                    artifact(name: 'b-transitive')
                    edge('com.acme.external:external:1.0', ':includedBuild', 'com.acme.external:external:2.0-SNAPSHOT') {
                        compositeSubstitute()
                        artifact(name: 'c-bar', fileName: 'c-bar.jar')
                    }
                }
            }
        }
    }

    def "context travels to transitive dependencies via external components (Maven)"() {
        given:
        mavenRepo.module('com.acme.external', 'external', '1.2')
            .dependsOn('com.acme.external', 'c', '0.1')
            .publish()
        createDirs("a", "b", "includedBuild")
        file('settings.gradle') << """
            include 'a', 'b'
            includeBuild 'includedBuild'
        """
        buildFile << """
            def buildType = Attribute.of('buildType', String)
            def flavor = Attribute.of('flavor', String)
            allprojects {
                repositories {
                    maven { url = '${mavenRepo.uri}' }
                }
                dependencies {
                    attributesSchema {
                        attribute(buildType)
                        attribute(flavor)
                    }
                }
            }
            project(':a') {
                configurations {
                    _compileFreeDebug.attributes { attribute(buildType, 'debug'); attribute(flavor, 'free') }
                    _compileFreeRelease.attributes { attribute(buildType, 'release'); attribute(flavor, 'free') }
                }
                dependencies {
                    _compileFreeDebug project(':b')
                    _compileFreeRelease project(':b')
                }
            }
            project(':b') {
                configurations.create('default')
                artifacts {
                    'default' file('b-transitive.jar')
                }
                dependencies {
                    'default'('com.acme.external:external:1.2')
                }
            }
        """
        resolve.prepare {
            config('_compileFreeDebug', 'checkDebug')
            config('_compileFreeRelease', 'checkRelease')
        }

        file('includedBuild/build.gradle') << """

            group = 'com.acme.external'
            version = '2.0-SNAPSHOT'

            def buildType = Attribute.of('buildType', String)
            def flavor = Attribute.of('flavor', String)
            dependencies {
                attributesSchema {
                    attribute(buildType)
                    attribute(flavor)
                }
            }

            configurations {
                foo.attributes { attribute(buildType, 'debug'); attribute(flavor, 'free') }
                bar.attributes { attribute(buildType, 'release'); attribute(flavor, 'free') }
            }

            ${fooAndBarJars()}
        """
        file('includedBuild/settings.gradle') << '''
            rootProject.name = 'c'
        '''

        when:
        run ':a:checkDebug'

        then:
        executedAndNotSkipped ':includedBuild:fooJar'
        notExecuted ':includedBuild:barJar'
        resolve.expectGraph {
            root(':a', 'test:a:') {
                project(':b', 'test:b:') {
                    artifact(name: 'b-transitive')
                    module('com.acme.external:external:1.2') {
                        edge('com.acme.external:c:0.1', ':includedBuild', 'com.acme.external:c:2.0-SNAPSHOT') {
                            compositeSubstitute()
                            artifact(name: 'c-foo', fileName: 'c-foo.jar')
                        }
                    }
                }
            }
        }

        when:
        run ':a:checkRelease'

        then:
        executedAndNotSkipped ':includedBuild:barJar'
        notExecuted ':includedBuild:fooJar'
        resolve.expectGraph {
            root(':a', 'test:a:') {
                project(':b', 'test:b:') {
                    artifact(name: 'b-transitive')
                    module('com.acme.external:external:1.2') {
                        edge('com.acme.external:c:0.1', ':includedBuild', 'com.acme.external:c:2.0-SNAPSHOT') {
                            compositeSubstitute()
                            artifact(name: 'c-bar', fileName: 'c-bar.jar')
                        }
                    }
                }
            }
        }
    }

    def "context travels to transitive dependencies via external components (Ivy)"() {
        given:
        ivyRepo.module('com.acme.external', 'external', '1.2')
            .dependsOn('com.acme.external', 'c', '0.1')
            .publish()
        createDirs("a", "b", "includedBuild")
        file('settings.gradle') << """
            include 'a', 'b'
            includeBuild 'includedBuild'
        """
        buildFile << """
            def buildType = Attribute.of('buildType', String)
            def flavor = Attribute.of('flavor', String)
            allprojects {
                repositories {
                    ivy { url = '${ivyRepo.uri}' }
                }
                dependencies {
                    attributesSchema {
                        attribute(buildType)
                        attribute(flavor)
                    }
                }
            }
            project(':a') {
                configurations {
                    _compileFreeDebug.attributes { attribute(buildType, 'debug'); attribute(flavor, 'free') }
                    _compileFreeRelease.attributes { attribute(buildType, 'release'); attribute(flavor, 'free') }
                }
                dependencies {
                    _compileFreeDebug project(':b')
                    _compileFreeRelease project(':b')
                }
            }
            project(':b') {
                configurations.create('default')
                artifacts {
                    'default' file('b-transitive.jar')
                }
                dependencies {
                    'default'('com.acme.external:external:1.2')
                }
            }
        """
        resolve.prepare {
            config('_compileFreeDebug', 'checkDebug')
            config('_compileFreeRelease', 'checkRelease')
        }

        file('includedBuild/build.gradle') << """

            group = 'com.acme.external'
            version = '2.0-SNAPSHOT'

            def buildType = Attribute.of('buildType', String)
            def flavor = Attribute.of('flavor', String)
            dependencies {
                attributesSchema {
                    attribute(buildType)
                    attribute(flavor)
                }
            }

            configurations {
                foo.attributes { attribute(buildType, 'debug'); attribute(flavor, 'free') }
                bar.attributes { attribute(buildType, 'release'); attribute(flavor, 'free') }
            }

            ${fooAndBarJars()}
        """
        file('includedBuild/settings.gradle') << '''
            rootProject.name = 'c'
        '''

        when:
        run ':a:checkDebug'
        resolve.expectGraph {
            root(':a', 'test:a:') {
                project(':b', 'test:b:') {
                    artifact(name: 'b-transitive')
                    module('com.acme.external:external:1.2') {
                        edge('com.acme.external:c:0.1', ':includedBuild', 'com.acme.external:c:2.0-SNAPSHOT') {
                            compositeSubstitute()
                            artifact(name: 'c-foo', fileName: 'c-foo.jar')
                        }
                    }
                }
            }
        }

        then:
        executedAndNotSkipped ':includedBuild:fooJar'
        notExecuted ':includedBuild:barJar'

        when:
        run ':a:checkRelease'

        then:
        executedAndNotSkipped ':includedBuild:barJar'
        notExecuted ':includedBuild:fooJar'
        resolve.expectGraph {
            root(':a', 'test:a:') {
                project(':b', 'test:b:') {
                    artifact(name: 'b-transitive')
                    module('com.acme.external:external:1.2') {
                        edge('com.acme.external:c:0.1', ':includedBuild', 'com.acme.external:c:2.0-SNAPSHOT') {
                            compositeSubstitute()
                            artifact(name: 'c-bar', fileName: 'c-bar.jar')
                        }
                    }
                }
            }
        }
    }

    def "attribute values are matched across builds - #type"() {
        given:
        createDirs("a", "b", "includedBuild")
        file('settings.gradle') << """
            include 'a', 'b'
            includeBuild 'includedBuild'
        """
        buildFile << """
            enum SomeEnum { free, paid }
            interface Thing extends Named { }
            @groovy.transform.EqualsAndHashCode
            class OtherThing implements Thing, Serializable { String name }

            def flavor = Attribute.of('flavor', $type)
            allprojects {
                dependencies {
                    attributesSchema {
                        attribute(flavor)
                    }
                }
            }
            project(':a') {
                configurations {
                    _compileFree.attributes { attribute(flavor, $freeValue) }
                    _compilePaid.attributes { attribute(flavor, $paidValue) }
                }
                dependencies {
                    _compileFree project(':b')
                    _compilePaid project(':b')
                }
            }
            project(':b') {
                configurations.create('default')
                artifacts {
                    'default' file('b-transitive.jar')
                }
                dependencies {
                    'default'('com.acme.external:external:1.0')
                }
            }
        """
        resolve.prepare {
            config('_compileFree', 'checkFree')
            config('_compilePaid', 'checkPaid')
        }

        file('includedBuild/build.gradle') << """
            enum SomeEnum { free, paid }
            interface Thing extends Named { }
            @groovy.transform.EqualsAndHashCode
            class OtherThing implements Thing, Serializable { String name }

            group = 'com.acme.external'
            version = '2.0-SNAPSHOT'

            def flavor = Attribute.of('flavor', $type)
            dependencies {
                attributesSchema {
                    attribute(flavor)
                }
            }

            configurations {
                foo.attributes { attribute(flavor, $freeValue) }
                bar.attributes { attribute(flavor, $paidValue) }
            }

            ${fooAndBarJars()}
        """
        file('includedBuild/settings.gradle') << '''
            rootProject.name = 'external'
        '''

        when:
        run ':a:checkFree'

        then:
        executedAndNotSkipped ':includedBuild:fooJar'
        notExecuted ':includedBuild:barJar'
        resolve.expectGraph {
            root(':a', 'test:a:') {
                project(':b', 'test:b:') {
                    artifact(name: 'b-transitive')
                    edge('com.acme.external:external:1.0', ':includedBuild', 'com.acme.external:external:2.0-SNAPSHOT') {
                        compositeSubstitute()
                        artifact(name: 'c-foo', fileName: 'c-foo.jar')
                    }
                }
            }
        }

        when:
        run ':a:checkPaid'

        then:
        executedAndNotSkipped ':includedBuild:barJar'
        notExecuted ':includedBuild:fooJar'
        resolve.expectGraph {
            root(':a', 'test:a:') {
                project(':b', 'test:b:') {
                    artifact(name: 'b-transitive')
                    edge('com.acme.external:external:1.0', ':includedBuild', 'com.acme.external:external:2.0-SNAPSHOT') {
                        compositeSubstitute()
                        artifact(name: 'c-bar', fileName: 'c-bar.jar')
                    }
                }
            }
        }

        where:
        type         | freeValue                      | paidValue
        'SomeEnum'   | 'SomeEnum.free'                | 'SomeEnum.paid'
        'Thing'      | 'objects.named(Thing, "free")' | 'objects.named(Thing, "paid")'
        'OtherThing' | 'new OtherThing(name: "free")' | 'new OtherThing(name: "paid")'
    }

    def "compatibility and disambiguation rules can be defined by consuming build"() {
        given:
        createDirs("a", "b", "includedBuild")
        file('settings.gradle') << """
            include 'a', 'b'
            includeBuild 'includedBuild'
        """
        buildFile << """
            interface Thing extends Named { }

            class CompatRule implements AttributeCompatibilityRule<Thing> {
                void execute(CompatibilityCheckDetails<Thing> details) {
                    if (details.consumerValue.name == 'paid' && details.producerValue.name == 'blue') {
                        details.compatible()
                    } else if (details.producerValue.name == 'red') {
                        details.compatible()
                    }
                }
            }

            class DisRule implements AttributeDisambiguationRule<Thing> {
                void execute(MultipleCandidatesDetails<Thing> details) {
                    for (Thing t: details.candidateValues) {
                        if (t.name == 'blue') {
                            details.closestMatch(t)
                            return
                        }
                    }
                }
            }

            def flavor = Attribute.of('flavor', Thing)
            allprojects {
                dependencies {
                    attributesSchema {
                        attribute(flavor).compatibilityRules.add(CompatRule)
                        attribute(flavor).disambiguationRules.add(DisRule)
                    }
                }
            }
            project(':a') {
                configurations {
                    _compileFree.attributes { attribute(flavor, objects.named(Thing, 'free')) }
                    _compilePaid.attributes { attribute(flavor, objects.named(Thing, 'paid')) }
                }
                dependencies {
                    _compileFree project(':b')
                    _compilePaid project(':b')
                }
            }
            project(':b') {
                configurations.create('default')
                artifacts {
                    'default' file('b-transitive.jar')
                }
                dependencies {
                    'default'('com.acme.external:external:1.0')
                }
            }
        """
        resolve.prepare {
            config('_compileFree', 'checkFree')
            config('_compilePaid', 'checkPaid')
        }

        file('includedBuild/build.gradle') << """
            interface Thing extends Named { }

            group = 'com.acme.external'
            version = '2.0-SNAPSHOT'

            def flavor = Attribute.of('flavor', Thing)
            dependencies {
                attributesSchema {
                    attribute(flavor)
                }
            }

            configurations {
                foo.attributes { attribute(flavor, objects.named(Thing, 'red')) }
                bar.attributes { attribute(flavor, objects.named(Thing, 'blue')) }
            }

            ${fooAndBarJars()}
        """
        file('includedBuild/settings.gradle') << '''
            rootProject.name = 'external'
        '''

        when:
        run ':a:checkFree'

        then:
        executedAndNotSkipped ':includedBuild:fooJar'
        notExecuted ':includedBuild:barJar'
        resolve.expectGraph {
            root(':a', 'test:a:') {
                project(':b', 'test:b:') {
                    artifact(name: 'b-transitive')
                    edge('com.acme.external:external:1.0', ':includedBuild', 'com.acme.external:external:2.0-SNAPSHOT') {
                        compositeSubstitute()
                        artifact(name: 'c-foo', fileName: 'c-foo.jar')
                    }
                }
            }
        }

        when:
        run ':a:checkPaid'

        then:
        executedAndNotSkipped ':includedBuild:barJar'
        notExecuted ':includedBuild:fooJar'
        resolve.expectGraph {
            root(':a', 'test:a:') {
                project(':b', 'test:b:') {
                    artifact(name: 'b-transitive')
                    edge('com.acme.external:external:1.0', ':includedBuild', 'com.acme.external:external:2.0-SNAPSHOT') {
                        compositeSubstitute()
                        artifact(name: 'c-bar', fileName: 'c-bar.jar')
                    }
                }
            }
        }
    }

    def "reports failure to resolve due to incompatible attribute values"() {
        given:
        createDirs("a", "b", "includedBuild")
        file('settings.gradle') << """
            include 'a', 'b'
            includeBuild 'includedBuild'
        """
        buildFile << """
            interface Thing extends Named { }

            class CompatRule implements AttributeCompatibilityRule<Thing> {
                void execute(CompatibilityCheckDetails<Thing> details) {
                    if (details.consumerValue.name == 'paid') {
                        details.compatible()
                    }
                }
            }

            def flavor = Attribute.of('flavor', Thing)
            allprojects {
                dependencies {
                    attributesSchema {
                        attribute(flavor).compatibilityRules.add(CompatRule)
                    }
                }
            }
            project(':a') {
                configurations {
                    _compileFree.attributes { attribute(flavor, objects.named(Thing, 'free')) }
                    _compilePaid.attributes { attribute(flavor, objects.named(Thing, 'paid')) }
                }
                dependencies {
                    _compileFree project(':b')
                    _compilePaid project(':b')
                }
                task checkFree(dependsOn: configurations._compileFree) {
                    doLast {
                       assert configurations._compileFree.collect { it.name } == ['b-transitive.jar', 'c-foo.jar']
                    }
                }
                task checkPaid(dependsOn: configurations._compilePaid) {
                    doLast {
                       assert configurations._compilePaid.collect { it.name } == ['b-transitive.jar', 'c-bar.jar']
                    }
                }
            }
            project(':b') {
                configurations.create('default')
                artifacts {
                    'default' file('b-transitive.jar')
                }
                dependencies {
                    'default'('com.acme.external:external:1.0')
                }
            }
        """

        file('includedBuild/build.gradle') << """
            interface Thing extends Named { }

            group = 'com.acme.external'
            version = '2.0-SNAPSHOT'

            def flavor = Attribute.of('flavor', Thing)
            dependencies {
                attributesSchema {
                    attribute(flavor)
                }
            }

            configurations {
                foo.attributes { attribute(flavor, objects.named(Thing, 'red')) }
                bar.attributes { attribute(flavor, objects.named(Thing, 'blue')) }
            }

            ${fooAndBarJars()}
        """
        file('includedBuild/settings.gradle') << '''
            rootProject.name = 'external'
        '''

        when:
        fails ':a:checkFree'

        then:
        failure.assertHasCause("Could not resolve com.acme.external:external:1.0.")
        failure.assertHasCause("""No matching variant of project :includedBuild was found. The consumer was configured to find attribute 'flavor' with value 'free' but:
  - Variant 'bar' capability com.acme.external:external:2.0-SNAPSHOT:
      - Incompatible because this component declares attribute 'flavor' with value 'blue' and the consumer needed attribute 'flavor' with value 'free'
  - Variant 'foo' capability com.acme.external:external:2.0-SNAPSHOT:
      - Incompatible because this component declares attribute 'flavor' with value 'red' and the consumer needed attribute 'flavor' with value 'free'""")

        when:
        fails ':a:checkPaid'

        then:
        failure.assertHasCause("Could not resolve com.acme.external:external:1.0.")
        failure.assertHasCause("""The consumer was configured to find attribute 'flavor' with value 'paid'. However we cannot choose between the following variants of project :includedBuild:
  - bar
  - foo
All of them match the consumer attributes:
  - Variant 'bar' capability com.acme.external:external:2.0-SNAPSHOT declares attribute 'flavor' with value 'blue'
  - Variant 'foo' capability com.acme.external:external:2.0-SNAPSHOT declares attribute 'flavor' with value 'red'""")
    }

    def "context travels down to transitive dependencies with typed attributes using plugin"() {
        def resolve = new ResolveTestFixture(buildFile)

        buildTypedAttributesPlugin('1.0')
        buildTypedAttributesPlugin('1.1')

        given:
        createDirs("a", "b", "includedBuild")
        settingsFile.text = """
            pluginManagement {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
            }
            include 'a', 'b'
            includeBuild 'includedBuild'
            rootProject.name = 'test'
        """
        buildFile << """
            ${usesTypedAttributesPlugin(v1, usePluginsDSL)}

            allprojects {
                dependencies {
                    attributesSchema {
                        attribute(flavor)
                        attribute(buildType)
                    }
                }
            }
            project(':a') {
                configurations {
                    _compileFreeDebug.attributes { attribute(buildType, debug); attribute(flavor, free) }
                    _compileFreeRelease.attributes { attribute(buildType, release); attribute(flavor, free) }
                }
                dependencies {
                    _compileFreeDebug project(':b')
                    _compileFreeRelease project(':b')
                }
                task xcheckDebug(dependsOn: configurations._compileFreeDebug) {
                    doLast {
                       assert configurations._compileFreeDebug.collect { it.name } == ['b-transitive.jar', 'c-foo.jar']
                    }
                }
                task xcheckRelease(dependsOn: configurations._compileFreeRelease) {
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
                    'default'('com.acme.external:external:1.0')
                }
            }
        """
        resolve.prepare {
            config("_compileFreeDebug", "checkDebug")
            config("_compileFreeRelease", "checkRelease")
        }

        file('includedBuild/build.gradle') << """
            ${usesTypedAttributesPlugin(v2, usePluginsDSL)}

            group = 'com.acme.external'
            version = '2.0-SNAPSHOT'

            dependencies {
                attributesSchema {
                    attribute(buildType)
                    attribute(flavor)
                }
            }

            configurations {
                foo.attributes { attribute(buildType, debug); attribute(flavor, free) }
                bar.attributes { attribute(buildType, release); attribute(flavor, free) }
            }

            ${fooAndBarJars()}
        """

        file('includedBuild/settings.gradle') << """
            pluginManagement {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
            }
            rootProject.name = 'external'
        """

        when:
        run ':a:checkDebug'

        then:
        executedAndNotSkipped ':includedBuild:fooJar'
        notExecuted ':includedBuild:barJar'
        resolve.expectGraph {
            root(':a', 'test:a:') {
                project(':b', 'test:b:') {
                    artifact(name: 'b-transitive')
                    edge('com.acme.external:external:1.0', ':includedBuild', 'com.acme.external:external:2.0-SNAPSHOT') {
                        compositeSubstitute()
                        artifact(name: 'c-foo', fileName: 'c-foo.jar')
                    }
                }
            }
        }

        when:
        run ':a:checkRelease'

        then:
        executedAndNotSkipped ':includedBuild:barJar'
        notExecuted ':includedBuild:fooJar'
        resolve.expectGraph {
            root(':a', 'test:a:') {
                project(':b', 'test:b:') {
                    artifact(name: 'b-transitive')
                    edge('com.acme.external:external:1.0', ':includedBuild', 'com.acme.external:external:2.0-SNAPSHOT') {
                        compositeSubstitute()
                        artifact(name: 'c-bar', fileName: 'c-bar.jar')
                    }
                }
            }
        }

        where:
        v1    | v2    | usePluginsDSL
        '1.0' | '1.0' | false
        '1.1' | '1.0' | false

        '1.0' | '1.0' | true
        '1.1' | '1.0' | true
    }

    private String usesTypedAttributesPlugin(String version, boolean usePluginsDSL) {
        String pluginsBlock = usePluginsDSL ? """
            plugins {
                id 'com.acme.typed-attributes' version '$version'
            } """ : """
            buildscript {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
                dependencies {
                    classpath 'com.acme.typed-attributes:com.acme.typed-attributes.gradle.plugin:$version'
                }
            }

            apply plugin: 'com.acme.typed-attributes'
            """

        """
            $pluginsBlock

            import static com.acme.Flavor.free
            import static com.acme.Flavor.paid
            import static com.acme.BuildType.debug
            import static com.acme.BuildType.release

            def flavor = Attribute.of(com.acme.Flavor)
            def buildType = Attribute.of(com.acme.BuildType)
        """
    }

    private void buildTypedAttributesPlugin(String version = "1.0") {
        def pluginDir = new File(testDirectory, "typed-attributes-plugin-$version")
        pluginDir.deleteDir()
        pluginDir.mkdirs()
        def builder = new FileTreeBuilder(pluginDir)
        builder.call {
            'settings.gradle'('rootProject.name="com.acme.typed-attributes.gradle.plugin"')
            'build.gradle'("""
                apply plugin: 'groovy'
                apply plugin: 'maven-publish'

                group = 'com.acme.typed-attributes'
                version = '$version'

                dependencies {
                    implementation localGroovy()
                    implementation gradleApi()
                }

                publishing {
                    repositories {
                        maven {
                            url "${mavenRepo.uri}"
                        }
                    }
                    publications {
                        maven(MavenPublication) { from components.java }
                    }
                }
            """)
            src {
                main {
                    groovy {
                        com {
                            acme {
                                'Flavor.groovy'('package com.acme; enum Flavor { free, paid }')
                                'BuildType.groovy'('package com.acme; enum BuildType { debug, release }')
                                'TypedAttributesPlugin.groovy'('''package com.acme

                                    import org.gradle.api.Plugin
                                    import org.gradle.api.Project
                                    import org.gradle.api.attributes.Attribute

                                    class TypedAttributesPlugin implements Plugin<Project> {
                                        void apply(Project p) {
                                            p.dependencies.attributesSchema {
                                                attribute(Attribute.of(Flavor))
                                                attribute(Attribute.of(BuildType))
                                            }
                                        }
                                    }
                                    ''')
                            }
                        }
                    }
                    resources {
                        'META-INF' {
                            'gradle-plugins' {
                                'com.acme.typed-attributes.properties'('implementation-class: com.acme.TypedAttributesPlugin')
                            }
                        }
                    }
                }
            }
        }
        executer.inDirectory(pluginDir)
            .withTasks("publishMavenPublicationToMavenRepository")
            .run()
    }

    private String fooAndBarJars() {
        '''
            task fooJar(type: Jar) {
                archiveBaseName = 'c-foo'
                destinationDirectory = projectDir
            }
            task barJar(type: Jar) {
                archiveBaseName = 'c-bar'
                destinationDirectory = projectDir
            }
            artifacts {
                foo fooJar
                bar barJar
            }
        '''
    }
}
