/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.plugins.ide.tooling.r56

import org.gradle.integtests.fixtures.RepoScriptBlockUtil
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.model.eclipse.EclipseClasspathEntry
import org.gradle.tooling.model.eclipse.EclipseProject

@ToolingApiVersion('>=5.6')
@TargetGradleVersion(">=5.6")
class ToolingApiEclipseModelTestSourcesCrossVersionSpec extends ToolingApiSpecification {

    def "Test source folders and dependencies has test attribute"() {
        setup:
        buildFile << """
            plugins {
                id 'java-library'
            }

            ${RepoScriptBlockUtil.jcenterRepository()}

            dependencies {
                implementation 'com.google.guava:guava:21.0'
                testImplementation 'junit:junit:4.12'
            }
        """
        file('src/main/java').mkdirs()
        file('src/test/java').mkdirs()

        when:
        EclipseProject project = loadToolingModel(EclipseProject)
        def mainSrcDir = project.sourceDirectories.find { it.path == 'src/main/java' }
        def testSrcDir = project.sourceDirectories.find { it.path == 'src/test/java' }
        def guava = project.classpath.find { it.file.name.contains 'guava' }
        def junit = project.classpath.find { it.file.name.contains 'junit' }

        then:
        !hasTestAttributes(mainSrcDir)

        and:
        hasTestAttributes(testSrcDir)

        and:
        !hasTestAttributes(guava)

        and:
        hasTestAttributes(junit)
    }

    private boolean hasTestAttributes(EclipseClasspathEntry entry) {
        entry.classpathAttributes.find { it.name == 'test' && it.value == 'true' }
    }
}
