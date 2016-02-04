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

package org.gradle.integtests.tooling.fixture

import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import org.gradle.test.fixtures.file.TestFile
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.composite.GradleConnection

@ToolingApiVersion('>=2.12')
@TargetGradleVersion('>=2.12')
abstract class CompositeToolingApiSpecification extends ToolingApiSpecification {

    GradleConnection createComposite(File... rootProjectDirectories) {
        createComposite(rootProjectDirectories as List<File>)
    }

    GradleConnection createComposite(List<File> rootProjectDirectories) {
        GradleConnection.Builder builder = createCompositeBuilder()

        rootProjectDirectories.each {
            // TODO: this isn't the right way to configure the gradle distribution
            builder.addBuild(it, dist.binDistribution.toURI())
        }

        builder.build()
    }

    GradleConnection.Builder createCompositeBuilder() {
        GradleConnection.Builder builder = GradleConnector.newGradleConnectionBuilder()

        // TODO: Pull this from a nicer place?
        builder.useGradleUserHomeDir(toolingApi.gradleUserHomeDir)
        builder
    }

    void withCompositeConnection(File rootProjectDir, @ClosureParams(value = SimpleType, options = [ "org.gradle.tooling.composite.GradleConnection" ]) Closure c) {
        withCompositeConnection([rootProjectDir], c)
    }

    void withCompositeConnection(List<File> rootProjectDirectories, @ClosureParams(value = SimpleType, options = [ "org.gradle.tooling.composite.GradleConnection" ]) Closure c) {
        GradleConnection connection
        try {
            connection = createComposite(rootProjectDirectories)
            c(connection)
        } finally {
            connection?.close()
        }
    }

    TestFile getRootDir() {
        temporaryFolder.testDirectory
    }

    TestFile getProjectDir() {
        // TODO: Refactor ToolingApiSpecification
        throw new RuntimeException("need to specific participant")
    }

    TestFile getBuildFile() {
        // TODO: Refactor ToolingApiSpecification
        throw new RuntimeException("need to specific participant")
    }

    TestFile getSettingsFile() {
        // TODO: Refactor ToolingApiSpecification
        throw new RuntimeException("need to specific participant")
    }

    TestFile file(Object... path) {
        rootDir.file(path)
    }

    ProjectTestFile populate(String projectName, @DelegatesTo(ProjectTestFile) Closure cl) {
        def project = new ProjectTestFile(rootDir, projectName)
        project.with(cl)
        project
    }

    TestFile projectDir(String project) {
        file(project)
    }

    class ProjectTestFile extends TestFile {
        private final String projectName

        ProjectTestFile(TestFile rootDir, String projectName) {
            super(rootDir, [ projectName ])
            this.projectName = projectName
        }
        String getRootProjectName() {
            projectName
        }
        TestFile getBuildFile() {
            file("build.gradle")
        }
        TestFile getSettingsFile() {
            file("settings.gradle")
        }
    }
}
