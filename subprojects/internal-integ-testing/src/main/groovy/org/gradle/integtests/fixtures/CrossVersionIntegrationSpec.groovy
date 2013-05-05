/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.integtests.fixtures

import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.integtests.fixtures.executer.UnderDevelopmentGradleDistribution
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.test.fixtures.maven.MavenFileRepository
import org.gradle.test.fixtures.maven.MavenRepository
import org.junit.Rule
import org.junit.runner.RunWith
import spock.lang.Specification

@RunWith(CrossVersionTestRunner)
abstract class CrossVersionIntegrationSpec extends Specification implements TestDirectoryProvider {
    @Rule TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()
    final GradleDistribution current = new UnderDevelopmentGradleDistribution()
    static GradleDistribution previous
    private MavenFileRepository mavenRepo

    GradleDistribution getPrevious() {
        return previous
    }

    protected TestFile getBuildFile() {
        testDirectory.file('build.gradle')
    }

    protected TestFile getSettingsFile() {
        testDirectory.file('settings.gradle')
    }

    TestFile getTestDirectory() {
        temporaryFolder.getTestDirectory();
    }

    protected TestFile file(Object... path) {
        testDirectory.file(path);
    }

    protected MavenRepository getMavenRepo() {
        if (mavenRepo == null) {
            mavenRepo = new MavenFileRepository(file("maven-repo"))
        }
        return mavenRepo
    }

    def version(GradleDistribution dist) {
        def executer = dist.executer(temporaryFolder)
        executer.withDeprecationChecksDisabled()
        executer.inDirectory(testDirectory)
        return executer;
    }
}
