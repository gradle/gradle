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

import static org.gradle.plugin.use.resolve.internal.ArtifactRepositoryPluginResolver.PLUGIN_MARKER_SUFFIX

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

    def "Publishes nothing if automated publishing is disabled"() {
        given:
        plugin('foo', 'com.example.foo')
        publishToIvy()
        publishToMaven()
        buildFile << """
            gradlePlugin {
                automatedPublishing = false
            }
        """

        when:
        succeeds 'publish'

        then:
        ivyRepo.module('com.example', 'plugins', '1.0').assertNotPublished()
        ivyRepo.module('com.example.foo', 'com.example.foo' + PLUGIN_MARKER_SUFFIX, '1.0').assertNotPublished()

        mavenRepo.module('com.example', 'plugins', '1.0').assertNotPublished()
        mavenRepo.module('com.example.foo', 'com.example.foo' + PLUGIN_MARKER_SUFFIX, '1.0').assertNotPublished()
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
        plugin('foo', 'com.example.foo')
        plugin('bar', 'com.example.bar')
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
    }

    def "Publishes one Maven marker for every plugin"() {
        given:
        plugin('foo', 'com.example.foo')
        plugin('bar', 'com.example.bar')
        publishToMaven()

        when:
        succeeds 'publish'

        then:
        def fooMarker = mavenRepo.module('com.example.foo', 'com.example.foo' + PLUGIN_MARKER_SUFFIX, '1.0')
        def barMarker = mavenRepo.module('com.example.bar', 'com.example.bar' + PLUGIN_MARKER_SUFFIX, '1.0')
        [fooMarker, barMarker].each { marker ->
            marker.assertPublished()
            assert marker.parsedPom.scopes['runtime'].expectDependency('com.example:plugins:1.0')
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

    def plugin(String name, String pluginId) {
        String implementationClass = name.capitalize();

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
                    }
                }
            }
        """
    }
}
