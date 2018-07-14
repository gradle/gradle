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

package org.gradle.smoketests

import org.apache.commons.io.FileUtils
import org.gradle.integtests.fixtures.ReplaceExternalRepos
import org.gradle.integtests.fixtures.RepoScriptBlockUtil
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.internal.DefaultGradleRunner
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

abstract class AbstractSmokeTest extends Specification {

    @Rule
    final TemporaryFolder testProjectDir = new TemporaryFolder()
    File buildFile

    def setup() {
        buildFile = new File(testProjectDir.root, defaultBuildFileName)
    }

    protected String getDefaultBuildFileName() {
        'build.gradle'
    }

    File file(String filename) {
        def file = new File(testProjectDir.root, filename)
        def parentDir = file.getParentFile()
        assert parentDir.isDirectory() || parentDir.mkdirs()

        file
    }

    GradleRunner runner(String... tasks) {
        new RepoMirrorGradleRunner()
            .withGradleInstallation(IntegrationTestBuildContext.INSTANCE.gradleHomeDir)
            .withTestKitDir(IntegrationTestBuildContext.INSTANCE.gradleUserHomeDir)
            .withProjectDir(testProjectDir.root)
            .withArguments(tasks.toList() + ['-s'])
    }

    private static class RepoMirrorGradleRunner extends DefaultGradleRunner {
        @Override
        DefaultGradleRunner withProjectDir(File projectDir) {
            ReplaceExternalRepos.replaceExternalRepos(projectDir)
            super.withProjectDir(projectDir)
        }
    }

    protected void useSample(String sampleDirectory) {
        def smokeTestDirectory = new File(this.getClass().getResource(sampleDirectory).toURI())
        FileUtils.copyDirectory(smokeTestDirectory, testProjectDir.root)
    }

    protected void replaceVariablesInBuildFile(Map binding) {
        String text = buildFile.text
        binding.each { String var, String value ->
            text = text.replaceAll("\\\$${var}".toString(), value)
        }
        buildFile.text = text
    }

    protected static String jcenterRepository() {
        RepoScriptBlockUtil.jcenterRepository()
    }

    protected static String mavenCentralRepository() {
        RepoScriptBlockUtil.mavenCentralRepository()
    }

    protected static String googleRepository() {
        RepoScriptBlockUtil.googleRepository()
    }
}
