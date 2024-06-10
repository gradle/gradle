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

package org.gradle.plugins.ide.tooling.r30

import org.gradle.api.JavaVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.model.eclipse.EclipseClasspathContainer
import org.gradle.tooling.model.eclipse.EclipseProject
import spock.lang.Issue

import static org.gradle.plugins.ide.tooling.r210.ConventionsExtensionsCrossVersionFixture.javaTargetCompatibility

class ToolingApiEclipseModelClasspathContainerCrossVersionSpec extends ToolingApiSpecification {

    def setup() {
        settingsFile << 'rootProject.name = "root"'
    }

    def "Project has no classpath containers"() {
        when:
        EclipseProject project = loadToolingModel(EclipseProject)

        then:
        project.classpathContainers.isEmpty()
    }

    def "Project has some classpath containers"() {
        buildFile <<
        """apply plugin: 'java'
           apply plugin: 'eclipse'
           eclipse {
               classpath {
                   containers 'containerPath1', 'containerPath2'
               }
           }
        """

        when:
        EclipseProject project = loadToolingModel(EclipseProject)

        then:
        project.classpathContainers.size() == 3
        project.classpathContainers.find { it.path.startsWith 'org.eclipse.jdt.launching.JRE_CONTAINER' }
        project.classpathContainers.find { it.path == 'containerPath1' }
        project.classpathContainers.find { it.path == 'containerPath2' }
    }

    def "Classpath container defined in beforeMerged"() {
        buildFile <<
        """apply plugin: 'java'
           apply plugin: 'eclipse'
           eclipse {
               classpath {
                   file {
                      beforeMerged { classpath ->
                          classpath.entries.add(new org.gradle.plugins.ide.eclipse.model.Container('beforeMergedContainerPath'))
                      }
                   }
               }
           }
        """

        when:
        EclipseProject project = loadToolingModel(EclipseProject)

        then:
        project.classpathContainers.find { it.path == 'beforeMergedContainerPath' }
    }

    def "Classpath container defined in whenMerged"() {
        buildFile <<
        """apply plugin: 'java'
           apply plugin: 'eclipse'
           eclipse {
               classpath {
                   file {
                      whenMerged { classpath ->
                          classpath.entries.add(new org.gradle.plugins.ide.eclipse.model.Container('whenMergedContainerPath'))
                      }
                   }
               }
           }
        """

        when:
        EclipseProject project = loadToolingModel(EclipseProject)
        EclipseClasspathContainer container = project.classpathContainers.find { it.path == 'whenMergedContainerPath' }

        then:
        container != null
        container.exported == false
    }

    def "Classpath container can be configured"() {
        buildFile <<
        """apply plugin: 'java'
           apply plugin: 'eclipse'
           eclipse {
               classpath {
                   file {
                      whenMerged { classpath ->
                          def container = new org.gradle.plugins.ide.eclipse.model.Container('whenMergedContainerPath')
                          container.exported = true
                          classpath.entries.add(container)
                      }
                   }
               }
           }
        """

        when:
        EclipseProject project = loadToolingModel(EclipseProject)
        EclipseClasspathContainer container = project.classpathContainers.find { it.path == 'whenMergedContainerPath' }

        then:
        container != null
        container.exported == true
    }

    def "Respects targetCompatibility customization"() {
        setup:
        buildFile <<
        """apply plugin: 'java'
           apply plugin: 'eclipse'
           ${javaTargetCompatibility(targetVersion, JavaVersion.VERSION_1_4)}
        """

        when:
        EclipseProject project = loadToolingModel(EclipseProject)

        then:
        project.classpathContainers.find { it.path.startsWith('org.eclipse.jdt.launching.JRE_CONTAINER') && it.path.contains('1.4') }
    }

    def "Respects javaRuntimeName customization"() {
        setup:
        buildFile <<
        """apply plugin: 'java'
           apply plugin: 'eclipse'
           eclipse {
               jdt {
                    javaRuntimeName = "customJavaRuntime"
               }
           }
        """

        when:
        EclipseProject project = loadToolingModel(EclipseProject)

        then:
        project.classpathContainers.find { it.path.startsWith('org.eclipse.jdt.launching.JRE_CONTAINER') && it.path.contains('customJavaRuntime') }
    }

    def "javaRuntimeName customization wins over targetCompatibility"() {
        setup:
        buildFile <<
        """apply plugin: 'java'
           apply plugin: 'eclipse'
           ${javaTargetCompatibility(targetVersion, JavaVersion.VERSION_1_4)}
           eclipse {
               jdt {
                    javaRuntimeName = "customJavaRuntime"
               }
           }
        """

        when:
        EclipseProject project = loadToolingModel(EclipseProject)

        then:
        project.classpathContainers.find { it.path.startsWith('org.eclipse.jdt.launching.JRE_CONTAINER') && it.path.contains('customJavaRuntime') }
    }

    @Issue('https://issues.gradle.org/browse/GRADLE-3231')
    def "Whether or not the eclipse plugin is explicitly applied, the same model is retrieved "() {
        setup:
        buildFile << """
            apply plugin: 'java'
            ${eclipsePluginApplied ? "apply plugin: 'eclipse'" : ""}
        """

        when:
        EclipseProject project = loadToolingModel(EclipseProject)

        then:
        // EclipsePlugin.configureEclipseClasspath() registers the JRE container in an afterEvaluate block
        !project.classpathContainers.isEmpty()

        where:
        eclipsePluginApplied << [false, true]
    }
}
