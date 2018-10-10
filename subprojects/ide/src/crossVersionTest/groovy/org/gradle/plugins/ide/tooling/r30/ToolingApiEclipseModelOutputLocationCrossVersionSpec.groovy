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

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.model.UnsupportedMethodException
import org.gradle.tooling.model.eclipse.EclipseOutputLocation
import org.gradle.tooling.model.eclipse.EclipseProject

@ToolingApiVersion('>=3.0')
@TargetGradleVersion(">=3.0")
class ToolingApiEclipseModelOutputLocationCrossVersionSpec extends ToolingApiSpecification {

    def setup() {
        settingsFile << 'rootProject.name = "root"'
    }

    @TargetGradleVersion(">=2.6 <3.0")
    def "Old versions throw runtime exception when querying output location"() {
        when:
        EclipseProject project = loadToolingModel(EclipseProject)
        project.getOutputLocation()

        then:
        thrown UnsupportedMethodException
    }

    @TargetGradleVersion(">=3.0 <4.4")
    def "Non-Java project has default output location"() {
        when:
        EclipseProject project = loadToolingModel(EclipseProject)
        EclipseOutputLocation output = project.getOutputLocation()

        then:
        output.path == 'bin'
    }

    @TargetGradleVersion(">=3.0 <4.4")
    def "Java project has default output location"() {
        setup:
        buildFile << "apply plugin: 'java'"
        EclipseProject project = loadToolingModel(EclipseProject)

        when:
        EclipseOutputLocation output = project.getOutputLocation()

        then:
        output.path == 'bin'
    }

    def "Custom output location defined in dsl"() {
        setup:
        buildFile <<
        """apply plugin: 'java'
           apply plugin: 'eclipse'
           eclipse {
               classpath {
                   defaultOutputDir = file('custom-bin')
               }
           }
        """

        when:
        EclipseProject project = loadToolingModel(EclipseProject)
        EclipseOutputLocation output = project.getOutputLocation()

        then:
        output.path == 'custom-bin'
    }

    def "Custom output location defined in whenMerged"() {
        setup:
        buildFile <<
        """apply plugin: 'java'
           apply plugin: 'eclipse'
           eclipse {
               classpath {
                   file {
                       whenMerged { classpath ->
                           classpath.entries.find { it.kind == 'output' }.path = 'custom-bin'
                       }
                   }
               }
           }
        """

        when:
        EclipseProject project = loadToolingModel(EclipseProject)
        EclipseOutputLocation output = project.getOutputLocation()

        then:
        output.path == 'custom-bin'
    }

    def "If output location removed during configuration, then the default path is returned"() {
        setup:
        buildFile <<
        """apply plugin: 'java'
           apply plugin: 'eclipse'
           eclipse {
               classpath {
                   file {
                       whenMerged { classpath ->
                           classpath.entries.removeAll { it.kind == 'output' }
                       }
                   }
               }
           }
        """

        when:
        EclipseProject project = loadToolingModel(EclipseProject)
        EclipseOutputLocation output = project.getOutputLocation()

        then:
        output.path == 'bin'
    }

    def "If multiple output folder is configured, then the last one is used"() {
        setup:
        buildFile <<
        """apply plugin: 'java'
           apply plugin: 'eclipse'
           eclipse {
               classpath {
                   file {
                       whenMerged { classpath ->
                           classpath.entries.removeAll { it.kind == 'output' }
                           classpath.entries.add(new org.gradle.plugins.ide.eclipse.model.Output('$firstPath'))
                           classpath.entries.add(new org.gradle.plugins.ide.eclipse.model.Output('$secondPath'))
                       }
                   }
               }
           }
        """

        when:
        EclipseProject project = loadToolingModel(EclipseProject)
        EclipseOutputLocation output = project.getOutputLocation()

        then:
        output.path == secondPath

        where:
        firstPath | secondPath
        'first'   | 'second'
        'second'  | 'first'
    }


}
