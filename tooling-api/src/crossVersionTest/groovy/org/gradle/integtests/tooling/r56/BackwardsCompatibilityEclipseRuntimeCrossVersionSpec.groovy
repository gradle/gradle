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

package org.gradle.integtests.tooling.r56

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.model.eclipse.EclipseWorkspace
import org.gradle.tooling.model.eclipse.EclipseWorkspaceProject

import java.util.regex.Pattern

@TargetGradleVersion(">=5.6")
@ToolingApiVersion("=5.5")
class BackwardsCompatibilityEclipseRuntimeCrossVersionSpec extends ToolingApiSpecification {

    def "will not fail with older tooling versions"() {
        setup:
        multiProjectBuildInRootFolder("root", ["child1", "child2"]) {
            buildFile << """
            subprojects {
                apply plugin: 'java-library'
            }
            project(":child2") {
                dependencies {
                    implementation project(":child1");
                }
            }
        """
        }

        def out = new ByteArrayOutputStream()
        def workspace = eclipseWorkspace([gradleProject("child1"), gradleProject("child2")])
        when:
        withConnection { connection ->
            connection.action(new LoadEclipseModel(workspace))
                .setStandardOutput(out)
                .forTasks()
                .run()
        }

        then:
        !taskExecuted(out, ":eclipseClosedDependencies")
    }

    private static def taskExecuted(ByteArrayOutputStream out, String taskPath) {
        out.toString().find("(?m)> Task ${Pattern.quote(taskPath)}\$") != null
    }

    EclipseWorkspace eclipseWorkspace(List<EclipseWorkspaceProject> projects) {
        new DefaultEclipseWorkspace(temporaryFolder.file("workspace"), projects)
    }

    EclipseWorkspaceProject gradleProject(String name) {
        project(name, file(name))
    }

    static EclipseWorkspaceProject project(String name, File location) {
        // use map coercion here as this otherwise wouldn't compile against gradle 5.6+
        [getName: { name }, getLocation: { location }] as EclipseWorkspaceProject
    }

}
