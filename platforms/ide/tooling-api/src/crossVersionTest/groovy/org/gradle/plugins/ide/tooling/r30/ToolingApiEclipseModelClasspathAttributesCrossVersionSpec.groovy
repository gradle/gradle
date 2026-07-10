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


import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.WithOldConfigurationsSupport
import org.gradle.tooling.model.eclipse.EclipseProject

class ToolingApiEclipseModelClasspathAttributesCrossVersionSpec extends ToolingApiSpecification implements WithOldConfigurationsSupport {

    def "Eclipse model provides javadoc location via classpath attributes"() {
        setup:
        settingsFile << 'rootProject.name = "root"'
        buildFile <<
        """apply plugin: 'java'
           apply plugin: 'eclipse'
           ${mavenCentralRepository()}
           dependencies { ${implementationConfiguration} 'com.google.guava:guava:18.0' }
           eclipse {
               classpath {
                   downloadJavadoc = true
               }
           }
        """

        when:
        EclipseProject project = loadToolingModel(EclipseProject)
        def attributes = project.classpath[0].classpathAttributes

        then:
        attributes.find { it.name == 'javadoc_location' && it.value.contains('guava-18.0-javadoc.jar') }
    }

    def "Eclipse model provides classpath container attributes"() {
        setup:
        settingsFile << 'rootProject.name = "root"'
        buildFile <<
        """apply plugin: 'java'
           apply plugin: 'eclipse'
           eclipse {
               classpath {
                   containers 'containerPath'
                   file {
                       whenMerged { classpath ->
                           classpath.entries.find { it.path == 'containerPath' }.entryAttributes.customKey = 'customValue'
                       }
                   }
               }
           }
        """

        when:
        EclipseProject project = loadToolingModel(EclipseProject)
        def container = project.classpathContainers.find { it.path == 'containerPath' }

        then:
        container.classpathAttributes.size() == 1
        container.classpathAttributes[0].name == 'customKey'
        container.classpathAttributes[0].value == 'customValue'

    }


}
