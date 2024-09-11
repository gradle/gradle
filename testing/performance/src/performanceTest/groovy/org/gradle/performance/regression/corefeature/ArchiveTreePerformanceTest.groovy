/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.performance.regression.corefeature

import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.RandomStringUtils
import org.gradle.performance.AbstractCrossVersionPerformanceTest
import org.gradle.performance.annotations.RunFor
import org.gradle.performance.annotations.Scenario
import org.gradle.profiler.BuildContext
import org.gradle.profiler.BuildMutator
import org.gradle.profiler.InvocationSettings
import org.gradle.test.fixtures.file.TestFile

import static org.gradle.performance.annotations.ScenarioType.PER_COMMIT
import static org.gradle.performance.annotations.ScenarioType.PER_DAY
import static org.gradle.performance.results.OperatingSystem.LINUX


class ArchiveTreePerformanceTest extends AbstractCrossVersionPerformanceTest {
    private static TestFile tempDir
    private static TestFile archiveContentsDir

    def setupSpec() {
        tempDir = new TestFile("build/tmp/tmp-archive-performance")
        archiveContentsDir = tempDir.file("tmp-archive-contents")
        if (!archiveContentsDir.exists()) {
            generateArchiveContents(archiveContentsDir)
        }
    }

    @RunFor(
        @Scenario(type = PER_COMMIT, operatingSystems = [LINUX], testProjects = ["archivePerformanceProject"])
    )
    def "visiting zip trees"() {
        given:
        runner.tasksToRun = ['visitZip']
        runner.addBuildMutator { createArchive(it, "archive.zip") { contents, output -> contents.zipTo(output) } }

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()
    }

    @RunFor(
        @Scenario(type = PER_DAY, operatingSystems = [LINUX], testProjects = ["archivePerformanceProject"])
    )
    def "visiting tar trees"() {
        given:
        runner.tasksToRun = ['visitTar']
        runner.addBuildMutator { createArchive(it, "archive.tar") { contents, output -> contents.tarTo(output) } }

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()
    }

    @RunFor(
        @Scenario(type = PER_DAY, operatingSystems = [LINUX], testProjects = ["archivePerformanceProject"])
    )
    def "visiting gzip tar trees"() {
        given:
        runner.tasksToRun = ['visitTarGz']
        runner.addBuildMutator { createArchive(it, "archive.tar.gz") { contents, output -> contents.tgzTo(output) } }

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()
    }

    @RunFor(
        @Scenario(type = PER_COMMIT, operatingSystems = [LINUX], testProjects = ["archivePerformanceProject"])
    )
    def "compressing zip"() {
        given:
        runner.tasksToRun = ['zip']
        runner.addBuildMutator { linkToArchiveContents(it, archiveContentsDir) }

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()
    }

    @RunFor(
        @Scenario(type = PER_COMMIT, operatingSystems = [LINUX], testProjects = ["archivePerformanceProject"])
    )
    def "packing tar"() {
        given:
        runner.tasksToRun = ['tar']
        runner.addBuildMutator { linkToArchiveContents(it, archiveContentsDir) }

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()
    }

    @RunFor(
        @Scenario(type = PER_COMMIT, operatingSystems = [LINUX], testProjects = ["archivePerformanceProject"])
    )
    def "compressing tar"() {
        given:
        runner.tasksToRun = ['tarGz']
        runner.addBuildMutator { linkToArchiveContents(it, archiveContentsDir) }

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()
    }

    private static BuildMutator createArchive(InvocationSettings invocationSettings, String name, Closure builder) {
        new BuildMutator() {
            @Override
            void beforeBuild(BuildContext context) {
                def archive = tempDir.file(name)
                if (!archive.exists()) {
                    builder(archiveContentsDir.usingNativeTools(), archive)
                }
                File target = new File(invocationSettings.projectDir, name)
                FileUtils.copyFile(archive, target)
            }
        }
    }

    private static BuildMutator linkToArchiveContents(InvocationSettings invocationSettings, File source) {
        new BuildMutator() {
            @Override
            void beforeBuild(BuildContext context) {
                TestFile target = new TestFile(invocationSettings.projectDir, "archive-contents")
                if (!target.exists()) {
                    target.createLink(source)
                }
            }
        }
    }

    private static sampleFileContent = RandomStringUtils.random(1500)

    private static generateArchiveContents(File target) {
        (1..10000).each { i ->
            def folder = new File(target, "folder$i")
            folder.mkdirs()
            new File(folder, "file.txt") << sampleFileContent
        }
    }
}
