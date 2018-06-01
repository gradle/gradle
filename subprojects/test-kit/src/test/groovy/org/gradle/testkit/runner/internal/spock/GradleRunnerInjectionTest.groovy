/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.testkit.runner.internal.spock

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.spock.ProjectDirProvider
import org.junit.ClassRule
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Shared
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path

class GradleRunnerInjectionTest extends Specification {
    @Shared
    private Path tempDir = Files.createTempDirectory null

    @Shared
    @ClassRule
    private TemporaryFolder sharedTestProjectDir = new TemporaryFolder()

    @Rule
    private TemporaryFolder testProjectDir

    @Shared
    private GradleRunner sharedGradleRunner

    @Shared
    @ProjectDirProvider({ tempDir })
    private GradleRunner sharedGradleRunnerWithCustomProjectDir

    private GradleRunner gradleRunner

    @ProjectDirProvider({ sharedTestProjectDir })
    private GradleRunner gradleRunnerWithCustomProjectDir

    def 'GradleRunner is injected as shared field'() {
        expect:
        sharedGradleRunner
    }

    def 'GradleRunner is injected as non-shared field'() {
        expect:
        gradleRunner
    }

    def 'GradleRunner is injected as parameter'(GradleRunner gradleRunnerParameter) {
        expect:
        gradleRunnerParameter
    }

    def 'each injected GradleRunner has its own project directory'(GradleRunner gradleRunnerParameter) {
        expect:
        gradleRunnerParameter.projectDir != gradleRunner.projectDir
        gradleRunnerParameter.projectDir != sharedGradleRunner.projectDir
        gradleRunner.projectDir != sharedGradleRunner.projectDir
    }

    def 'ProjectDirProvider annotated gradle runner parameter uses the configured directory'(@ProjectDirProvider({ testProjectDir.root }) GradleRunner gradleRunnerParameter) {
        expect:
        gradleRunnerParameter.projectDir == testProjectDir.root
    }

    def 'ProjectDirProvider annotated gradle runner field uses the configured directory'() {
        expect:
        gradleRunnerWithCustomProjectDir.projectDir == sharedTestProjectDir.root
    }

    def 'ProjectDirProvider annotated shared gradle runner field uses the configured directory'() {
        expect:
        sharedGradleRunnerWithCustomProjectDir.projectDir == tempDir.toFile()
    }
}
