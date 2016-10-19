/*
 * Copyright 2014 the original author or authors.
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

package org.gradle

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.internal.nativeintegration.jansi.JansiLibraryFactory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.GFileUtils
import org.junit.Rule
import spock.lang.Issue

class NativeServicesIntegrationTest extends AbstractIntegrationSpec {

    @Rule
    final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()

    final File nativeDir = new File(executer.gradleUserHomeDir, 'native')
    final File jansiDir = new File(nativeDir, 'jansi')
    final JansiLibraryFactory factory = new JansiLibraryFactory()

    def setup() {
        requireGradleDistribution()
    }

    def "native services libs are unpacked to gradle user home dir"() {
        when:
        quietExecutor().run()

        then:
        nativeDir.directory
    }

    @Issue("GRADLE-3573")
    def "jansi library is unpacked to gradle user home dir and isn't overwritten if existing"() {
        given:
        String libraryPath = factory.create().path
        File library = new File(jansiDir, libraryPath)
        String tmpDirJvmOpt = "-Djava.io.tmpdir=$tmpDir.testDirectory.absolutePath"

        when:
        customizedExecutor(tmpDirJvmOpt).run()

        then:
        library.exists()
        tmpDir.testDirectory.listFiles().length == 0
        long lastModified = library.lastModified()

        when:
        customizedExecutor(tmpDirJvmOpt).run()

        then:
        library.exists()
        tmpDir.testDirectory.listFiles().length == 0
        lastModified == library.lastModified()
    }

    @Issue("GRADLE-3573")
    def "can start Gradle even with broken Jansi library file"() {
        given:
        String libraryPath = factory.create().path
        File library = new File(tmpDir.testDirectory, libraryPath)
        GFileUtils.touch(library)
        File libraryDir = library.parentFile
        File tmpDir = tmpDir.createDir('tmp')
        String[] buildJvmOpts = ["-Djava.io.tmpdir=${tmpDir.absolutePath}",
                                 "-Dlibrary.jansi.path=$libraryDir.absolutePath"]

        when:
        customizedExecutor(buildJvmOpts).run()

        then:
        noExceptionThrown()
        library.file && library.size() == 0
        tmpDir.directory && tmpDir.listFiles().size() == 0
    }

    private GradleExecuter quietExecutor() {
        executer.withArguments('-q')
    }

    private GradleExecuter customizedExecutor(String... buildJvmOpts) {
        executer.withNoExplicitTmpDir().withBuildJvmOpts(buildJvmOpts)
    }
}
