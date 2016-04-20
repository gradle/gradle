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
import org.gradle.tooling.connection.GradleConnection
import org.gradle.tooling.connection.GradleConnectionBuilder
import org.gradle.tooling.connection.ModelResult
import org.gradle.util.GradleVersion

@ToolingApiVersion(ToolingApiVersions.SUPPORTS_COMPOSITE_BUILD)
@TargetGradleVersion(">=1.0")
abstract class CompositeToolingApiSpecification extends AbstractToolingApiSpecification {
    boolean executeTestWithIntegratedComposite = true

    void skipIntegratedComposite() {
        executeTestWithIntegratedComposite = false
    }

    static GradleVersion getTargetDistVersion() {
        // Create a copy to work around classloader issues
        GradleVersion.version(targetDist.version.version)
    }

    static GradleVersion getToolingApiVersion() {
        GradleVersion.current()
    }

    GradleConnection createComposite(File... rootProjectDirectories) {
        createComposite(rootProjectDirectories.toList())
    }

    GradleConnection createComposite(List<File> rootProjectDirectories) {
        GradleConnectionBuilder builder = createCompositeBuilder()

        rootProjectDirectories.each {
            addCompositeParticipant(builder, it)
        }

        builder.build()
    }

    GradleConnectionBuilder createCompositeBuilder() {
        // TODO:DAZ This isn't quite right: we should be testing _both_ integrated and non-integrated composite for version that support both
        if (executeTestWithIntegratedComposite && supportsIntegratedComposites()) {
            return toolingApi.createIntegratedCompositeBuilder()
        }
        return toolingApi.createCompositeBuilder()
    }

    // TODO:DAZ Integrate this into the test runner with an annotation
    boolean supportsIntegratedComposites() {
        ToolingApiVersions.supportsIntegratedComposite(toolingApiVersion, targetDistVersion)
    }

    void addCompositeParticipant(GradleConnectionBuilder builder, File rootDir) {
        toolingApi.addCompositeParticipant(builder, rootDir)
    }

    def <T> T withCompositeConnection(File rootProjectDir, Closure<T> c) {
        withCompositeConnection([rootProjectDir], c)
    }

    def <T> T withCompositeConnection(List<File> rootProjectDirectories, @ClosureParams(value = SimpleType, options = [ "org.gradle.tooling.connection.GradleConnection" ]) Closure<T> c) {
        GradleConnection connection = createComposite(rootProjectDirectories)
        try {
            return c(connection)
        } finally {
            connection?.close()
        }
    }

    def <T> T withCompositeConnection(GradleConnectionBuilder builder, @ClosureParams(value = SimpleType, options = [ "org.gradle.tooling.connection.GradleConnection" ]) Closure<T> c) {
        GradleConnection connection = builder.build()
        try {
            return c(connection)
        } finally {
            connection?.close()
        }
    }

    TestFile getRootDir() {
        temporaryFolder.testDirectory
    }

    TestFile file(Object... path) {
        rootDir.file(path)
    }

    def populate(String projectName, @DelegatesTo(ProjectTestFile) Closure cl) {
        def project = new ProjectTestFile(rootDir, projectName)
        project.with(cl)
        project
    }

    def singleProjectBuild(String projectName, @DelegatesTo(ProjectTestFile) Closure cl = {}) {
        def project = populate(projectName) {
            settingsFile << """
                rootProject.name = '${rootProjectName}'
            """

            buildFile << """
                group = 'org.test'
                version = '1.0'
            """
        }
        project.with(cl)
        return project
    }

    def multiProjectBuild(String projectName, List<String> subprojects, @DelegatesTo(ProjectTestFile) Closure cl = {}) {
        String subprojectList = subprojects.collect({"'$it'"}).join(',')
        def rootMulti = populate(projectName) {
            settingsFile << """
                rootProject.name = '${rootProjectName}'
                include ${subprojectList}
            """

            buildFile << """
                allprojects {
                    group = 'org.test'
                    version = '1.0'
                }
            """
        }
        rootMulti.with(cl)
        subprojects.each {
            rootMulti.file(it, 'dummy.txt') << "temp"
        }
        return rootMulti
    }

    TestFile projectDir(String project) {
        file(project)
    }

    static class ProjectTestFile extends TestFile {
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
        void addChildDir(String name) {
            file(name).file("build.gradle") << "// Dummy child build"
        }
    }

    // Transforms Iterable<ModelResult<T>> into Iterable<T>
    def unwrap(Iterable<ModelResult> modelResults) {
        modelResults.collect { it.model }
    }

    void assertFailure(Throwable failure, String... messages) {
        assert failure != null
        def causes = getCauses(failure)

        messages.each { message ->
            assert causes.contains(message)
        }
    }

    void assertFailureHasCause(Throwable failure, Class<Throwable> expectedType) {
        assert failure != null
        Throwable throwable = failure
        List causes = []
        while (throwable != null) {
            causes << throwable.getClass().getCanonicalName()
            throwable = throwable.cause
        }
        assert causes.contains(expectedType.getCanonicalName())
    }

    private static String getCauses(Throwable throwable) {
        def causes = '';
        while (throwable != null) {
            if (throwable.message != null) {
                causes += throwable.message
                causes += '\n'
            }
            throwable = throwable.cause
        }
        causes
    }
}
