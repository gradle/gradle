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
import org.gradle.integtests.fixtures.FluidDependenciesResolveRunner
import org.junit.runner.RunWith
import spock.lang.Unroll

@RunWith(FluidDependenciesResolveRunner)
class CompositeBuildConfigurationAttributesResolveIntegrationTest extends AbstractIntegrationSpec {

    def setup(){
        using m2
    }

    def "context travels down to transitive dependencies"() {
        given:
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
                        attribute(buildType).compatibilityRules.assumeCompatibleWhenMissing()
                        attribute(flavor).compatibilityRules.assumeCompatibleWhenMissing()
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
                    'default'('com.acme.external:external:1.0')
                }
            }
        '''

        file('includedBuild/build.gradle') << '''

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
        '''
        file('includedBuild/settings.gradle') << '''
            rootProject.name = 'external'
        '''

        when:
        run ':a:checkDebug'

        then:
        executedAndNotSkipped ':external:fooJar'
        notExecuted ':external:barJar'

        when:
        run ':a:checkRelease'

        then:
        executedAndNotSkipped ':external:barJar'
        notExecuted ':external:fooJar'
    }

    // This test documents the current behavior, NOT the expected one.
    // In particular, it's going to work for identical plugin versions on both ends
    // but will fail as soon as we use different versions because they will come from
    // different classloaders.
    @Unroll("context travels down to transitive dependencies with typed attributes using plugin [#v1, #v2, pluginsDSL=#usePluginsDSL]")
    def "context travels down to transitive dependencies with typed attributes"() {
        buildTypedAttributesPlugin('1.0')
        buildTypedAttributesPlugin('1.1')

        given:
        file('settings.gradle') << """
            pluginManagement {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
            }
            include 'a', 'b'
            includeBuild 'includedBuild'
        """
        buildFile << """
            ${usesTypedAttributesPlugin(v1, usePluginsDSL)}

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
                    _compileFreeDebug.attributes { attribute(buildType, debug); attribute(flavor, free) }
                    _compileFreeRelease.attributes { attribute(buildType, release); attribute(flavor, free) }
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
                    'default'('com.acme.external:external:1.0')
                }
            }
        """

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
        if (expectSuccess) {
            run ':a:checkDebug'
        } else {
            fails ':a:checkDebug'
        }

        then:
        if (expectSuccess) {
            executedAndNotSkipped ':external:fooJar'
            notExecuted ':external:barJar'
        } else {
            failure.assertHasDescription("Could not resolve all dependencies for configuration ':a:_compileFreeDebug'")
        }

        when:
        if (expectSuccess) {
            run ':a:checkRelease'
        } else {
            fails ':a:checkRelease'
        }

        then:
        if (expectSuccess) {
            executedAndNotSkipped ':external:barJar'
            notExecuted ':external:fooJar'
        } else {
            failure.assertHasDescription("Could not resolve all dependencies for configuration ':a:_compileFreeRelease'")
        }

        where:
        v1    | v2    | usePluginsDSL | expectSuccess
        '1.0' | '1.0' | false         | true
        '1.1' | '1.0' | false         | false
        '1.0' | '1.1' | false         | false

        '1.0' | '1.0' | true          | true
        '1.1' | '1.0' | true          | false
        '1.0' | '1.1' | true          | false
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

            import static com.acme.Flavor.*
            import static com.acme.BuildType.*

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
                apply plugin: 'maven'

                group = 'com.acme.typed-attributes'
                version = '$version'

                dependencies {
                    compile localGroovy()
                    compile gradleApi()
                }

                uploadArchives {
                    repositories {
                        mavenDeployer {
                            repository(url: "${mavenRepo.uri}")
                        }
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
        executer.usingBuildScript(new File(pluginDir, "build.gradle"))
            .withTasks("uploadArchives")
            .run()

    }
}
