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

package org.gradle.plugin.devel.plugins

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue

import static org.gradle.plugin.use.resolve.internal.ArtifactRepositoriesPluginResolver.PLUGIN_MARKER_SUFFIX

class JavaGradlePluginPluginPublishingIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        buildFile << """
            apply plugin: 'java-gradle-plugin'
            group = 'com.example'
            version = '1.0'
        """
        settingsFile << """
            rootProject.name = 'plugins'
        """
    }

    def "Publishes main plugin artifact to Ivy"() {
        given:
        plugin('foo', 'com.example.foo')
        publishToIvy()

        when:
        succeeds 'publish'

        then:

        ivyRepo.module('com.example', 'plugins', '1.0').assertPublished()
    }

    def "Publishes main plugin artifact to Maven"() {
        given:
        plugin('foo', 'com.example.foo')
        publishToMaven()

        when:
        succeeds 'publish'

        then:

        mavenRepo.module('com.example', 'plugins', '1.0').assertPublished()
    }

    def "Publishes one Ivy marker for every plugin"() {
        given:
        plugin('foo', 'com.example.foo', 'The Foo Plugin', 'The greatest Foo plugin of all time.')
        plugin('bar', 'com.example.bar', 'The Bar Plugin', 'The greatest Bar plugin of all time.')
        publishToIvy()

        when:
        succeeds 'publish'

        then:
        def fooMarker = ivyRepo.module('com.example.foo', 'com.example.foo' + PLUGIN_MARKER_SUFFIX, '1.0')
        def barMarker = ivyRepo.module('com.example.bar', 'com.example.bar' + PLUGIN_MARKER_SUFFIX, '1.0')
        [fooMarker, barMarker].each { marker ->
            marker.assertPublished()
            assert marker.parsedIvy.dependencies['com.example:plugins:1.0']
        }
        fooMarker.parsedIvy.description.text() == 'The greatest Foo plugin of all time.'
        barMarker.parsedIvy.description.text() == 'The greatest Bar plugin of all time.'
    }

    def "Publishes one Maven marker for every plugin"() {
        given:
        plugin('foo', 'com.example.foo', 'The Foo Plugin', 'The greatest Foo plugin of all time.')
        plugin('bar', 'com.example.bar', 'The Bar Plugin', 'The greatest Bar plugin of all time.')
        publishToMaven()

        when:
        succeeds 'publish'

        then:
        def fooMarker = mavenRepo.module('com.example.foo', 'com.example.foo' + PLUGIN_MARKER_SUFFIX, '1.0')
        def barMarker = mavenRepo.module('com.example.bar', 'com.example.bar' + PLUGIN_MARKER_SUFFIX, '1.0')
        [fooMarker, barMarker].each { marker ->
            marker.assertPublished()
            assert marker.parsedPom.scopes['compile'].expectDependency('com.example:plugins:1.0')
        }
        with(fooMarker.parsedPom) {
            it.name == 'The Foo Plugin'
            it.description == 'The greatest Foo plugin of all time.'
        }
        with(barMarker.parsedPom) {
            it.name == 'The Bar Plugin'
            it.description == 'The greatest Bar plugin of all time.'
        }
    }

    def "Can publish to Maven and Ivy at the same time"() {
        given:
        plugin('foo', 'com.example.foo')
        publishToMaven()
        publishToIvy()

        when:
        succeeds 'publish'

        then:

        mavenRepo.module('com.example', 'plugins', '1.0').assertPublished()
        mavenRepo.module('com.example.foo', 'com.example.foo' + PLUGIN_MARKER_SUFFIX, '1.0').assertPublished()

        ivyRepo.module('com.example', 'plugins', '1.0').assertPublished()
        ivyRepo.module('com.example.foo', 'com.example.foo' + PLUGIN_MARKER_SUFFIX, '1.0').assertPublished()
    }

    def "Can publish supplementary artifacts to both Maven and Ivy"() {

        given:
        plugin('foo', 'com.example.foo')
        publishToMaven()
        publishToIvy()

        and:
        buildFile << """

            task sourceJar(type: Jar) {
                archiveClassifier = "sources"
                from sourceSets.main.allSource
            }

            publishing {
                publications {
                    pluginMaven(MavenPublication) {
                        artifact sourceJar
                    }
                    pluginIvy(IvyPublication) {
                        artifact sourceJar
                    }
                }
            }

        """.stripIndent()

        when:
        succeeds 'publish'

        then:

        mavenRepo.module('com.example', 'plugins', '1.0')
            .assertArtifactsPublished("plugins-1.0.pom", "plugins-1.0.module", "plugins-1.0.jar", "plugins-1.0-sources.jar")
        mavenRepo.module('com.example.foo', 'com.example.foo' + PLUGIN_MARKER_SUFFIX, '1.0').assertPublished()

        ivyRepo.module('com.example', 'plugins', '1.0')
            .assertArtifactsPublished("ivy-1.0.xml", "plugins-1.0.module", "plugins-1.0.jar", "plugins-1.0-sources.jar")
        ivyRepo.module('com.example.foo', 'com.example.foo' + PLUGIN_MARKER_SUFFIX, '1.0').assertPublished()
    }

    @Issue("https://github.com/gradle/gradle/issues/23551")
    def "Can publish maven with changed artifactId"() {

        given:
        plugin('foo', 'com.example.foo')
        publishToMaven()

        and:
        buildFile << """
            publishing {
                afterEvaluate {
                    publications {
                        getByName("pluginMaven") {
                            configure {
                                artifactId = "foo-new"
                                groupId = "com.example.foo.new"
                                version = "1.2.3"
                            }
                        }
                    }
                }
            }
        """.stripIndent()

        when:
        succeeds 'publish'


        then:
        mavenRepo.module('com.example.foo.new', 'foo-new', '1.2.3').assertPublished()

        def module = mavenRepo.module('com.example.foo', 'com.example.foo' + PLUGIN_MARKER_SUFFIX, '1.0')
        module.assertPublished()
        module.getPomFile().text.contains('foo-new')
        module.getPomFile().text.contains('com.example.foo.new')
        module.getPomFile().text.contains('1.2.3')
    }

    def "Can publish ivy with changed artifactId"() {

        given:
        plugin('foo', 'com.example.foo')
        publishToIvy()

        and:
        buildFile << """
            publishing {
                afterEvaluate {
                    publications {
                        getByName("pluginIvy") {
                            configure {
                                module = "foo-new"
                                organisation = "com.example.foo.new"
                                revision = "1.2.3"
                            }
                        }
                    }
                }
            }
        """.stripIndent()

        when:
        succeeds 'publish'


        then:
        ivyRepo.module('com.example.foo.new', 'foo-new', '1.2.3').assertPublished()

        def module = ivyRepo.module('com.example.foo', 'com.example.foo' + PLUGIN_MARKER_SUFFIX, '1.0')
        module.assertPublished()
        module.parsedIvy.dependencies["com.example.foo.new:foo-new:1.2.3"] != null
    }

    def "Can handle unspecified version"() {
        given:
        buildFile << """
            version = null
        """
        plugin('foo', 'com.example.foo')
        publishToMaven()
        publishToIvy()


        when:
        succeeds 'publish'

        then:
        mavenRepo.module('com.example', 'plugins', 'unspecified').assertPublished()
        mavenRepo.module('com.example.foo', 'com.example.foo' + PLUGIN_MARKER_SUFFIX, 'unspecified').assertPublished()

        ivyRepo.module('com.example', 'plugins', 'unspecified').assertPublished()
        ivyRepo.module('com.example.foo', 'com.example.foo' + PLUGIN_MARKER_SUFFIX, 'unspecified').assertPublished()
    }

    def publishToMaven() {
        buildFile << """
            apply plugin: 'maven-publish'
            publishing {
                repositories {
                    maven {
                        url '${mavenRepo.uri}'
                    }
                }
            }
        """
    }

    def publishToIvy() {
        buildFile << """
            apply plugin: 'ivy-publish'
            publishing {
                repositories {
                    ivy {
                        url '${ivyRepo.uri}'
                    }
                }
            }
        """
    }

    def plugin(String name, String pluginId, String displayName = null, String description = null) {
        String implementationClass = name.capitalize()

        file("src/main/java/com/xxx/${implementationClass}.java") << """
package com.xxx;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
public class ${implementationClass} implements Plugin<Project> {
    public void apply(Project project) { }
}
"""
        buildFile << """
            gradlePlugin {
                plugins {
                    ${name} {
                        id = '${pluginId}'
                        implementationClass = '${implementationClass}'
                        ${displayName ? "displayName = '$displayName'" : ""}
                        ${description ? "description = '$description'" : ""}
                    }
                }
            }
        """
    }
}
