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
import org.gradle.integtests.fixtures.build.BuildTestFile
import org.gradle.integtests.fixtures.build.BuildTestFixture
import org.gradle.test.fixtures.file.TestFile
import org.gradle.tooling.connection.GradleConnection
import org.gradle.tooling.connection.GradleConnectionBuilder
import org.gradle.tooling.connection.ModelResult
import org.gradle.util.GradleVersion
import org.junit.runner.RunWith

@ToolingApiVersion(ToolingApiVersions.SUPPORTS_COMPOSITE_BUILD)
@TargetGradleVersion(">=1.2")
@RunWith(CompositeToolingApiCompatibilitySuiteRunner)
abstract class CompositeToolingApiSpecification extends AbstractToolingApiSpecification {

    private static final ThreadLocal<Boolean> INTEGRATED_COMPOSITE = new ThreadLocal<Boolean>()

    static void setIntegratedComposite(boolean enable) {
        INTEGRATED_COMPOSITE.set(enable)
    }

    static boolean isIntegratedComposite() {
        def integrated = INTEGRATED_COMPOSITE.get()
        return integrated != null && integrated
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
        if (isIntegratedComposite()) {
            return toolingApi.createIntegratedCompositeBuilder()
        }
        return toolingApi.createCompositeBuilder()
    }

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

    BuildTestFixture getBuildTestFixture() {
        new BuildTestFixture(temporaryFolder).withBuildInSubDir()
    }

    def populate(String projectName, @DelegatesTo(BuildTestFile) Closure cl) {
        buildTestFixture.populate(projectName, cl)
    }

    def singleProjectBuild(String projectName, @DelegatesTo(BuildTestFile) Closure cl = {}) {
        buildTestFixture.singleProjectBuild(projectName, cl)
    }

    def multiProjectBuild(String projectName, List<String> subprojects, @DelegatesTo(BuildTestFile) Closure cl = {}) {
        buildTestFixture.multiProjectBuild(projectName, subprojects, cl)
    }

    TestFile projectDir(String project) {
        file(project)
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
