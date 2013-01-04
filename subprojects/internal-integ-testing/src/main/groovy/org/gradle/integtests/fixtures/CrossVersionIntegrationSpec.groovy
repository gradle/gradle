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

import org.gradle.integtests.fixtures.executer.BasicGradleDistribution
import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.integtests.fixtures.executer.GradleDistributionExecuter
import org.gradle.test.fixtures.maven.MavenFileRepository
import org.gradle.test.fixtures.maven.MavenRepository
import org.gradle.util.TemporaryFolder
import org.gradle.util.TestFile
import org.gradle.util.TestWorkDirProvider
import org.junit.Rule
import org.junit.runner.RunWith
import spock.lang.Specification

@RunWith(CrossVersionTestRunner)
abstract class CrossVersionIntegrationSpec extends Specification implements TestWorkDirProvider {
    @Rule TemporaryFolder temporaryFolder = new TemporaryFolder()
    final GradleDistribution current = new GradleDistribution(this)
    static BasicGradleDistribution previous
    private MavenFileRepository mavenRepo

    BasicGradleDistribution getPrevious() {
        return previous
    }

    protected TestFile getBuildFile() {
        testWorkDir.file('build.gradle')
    }

    TestFile getTestWorkDir() {
        temporaryFolder.getTestWorkDir();
    }

    protected TestFile file(Object... path) {
        testWorkDir.file(path);
    }

    protected MavenRepository getMavenRepo() {
        if (mavenRepo == null) {
            mavenRepo = new MavenFileRepository(file("maven-repo"))
        }
        return mavenRepo
    }

    def version(BasicGradleDistribution dist) {
        def executer = dist.executer();
        if (executer instanceof GradleDistributionExecuter) {
            executer.withDeprecationChecksDisabled()
        }
        executer.inDirectory(testWorkDir)
        return executer;
    }
}
