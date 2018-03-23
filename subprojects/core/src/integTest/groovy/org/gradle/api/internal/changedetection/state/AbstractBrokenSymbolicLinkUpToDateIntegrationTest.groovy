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

package org.gradle.api.internal.changedetection.state

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

import java.nio.file.Files

@Requires(TestPrecondition.SYMLINKS)
abstract class AbstractBrokenSymbolicLinkUpToDateIntegrationTest extends AbstractIntegrationSpec {
    def "can handle a broken symbolic link"() {
        given:
        createCleanTask()
        makeScenarioProject()

        expect:
        succeeds("checkCreated")
        result.assertTaskNotSkipped(":checkCreated")
        succeeds("checkCreated")
        result.assertTaskSkipped(":checkCreated")

        succeeds("clean", "checkCreated")
        result.assertTaskNotSkipped(":checkCreated")

        succeeds("checkCreated")
        result.assertTaskSkipped(":checkCreated")
    }

    def "can detect changes to broken symbolic link target"() {
        given:
        makeScenarioProject()
        makeTaskUpToDate('checkCreated')

        expect:
        changeSymbolicLinkTarget(symbolicLinkUnderTest, alternateTargetFile)

        assertTaskIsOutOfDate("checkCreated")
    }

    def "can detect changes to broken symbolic link name"() {
        given:
        makeScenarioProject()
        makeTaskUpToDate('checkCreated')

        expect:
        symbolicLinkUnderTest.renameTo(symbolicLinkUnderTest.parentFile.file('new-sym-link'))

        assertTaskIsOutOfDate("checkCreated")
    }

    def "can detect new file completing a broken symbolic link"() {
        given:
        makeScenarioProject()
        makeTaskUpToDate('checkCreated')

        expect:
        targetFile.createFile()

        assertTaskIsOutOfDate("checkCreated")
    }

    // Just for task inputs
    def "can detect new empty directory completing a broken symbolic link"() {
        given:
        makeScenarioProject()
        makeTaskUpToDate('checkCreated')

        expect:
        targetFile.createDir()

        assertTaskIsOutOfDate("checkCreated")
    }

    def "can detect changes to the target file of a symbolic link"() {
        given:
        makeScenarioProject()
        targetFile.text = 'some text'

        makeTaskUpToDate('checkCreated')

        when:
        targetFile.text = 'some other text'

        then:
        assertTaskIsOutOfDate("checkCreated")
    }

    def "can detect changes to the target directory of a symbolic link"() {
        given:
        makeScenarioProject()
        targetFile.createDir()
        targetFile.file('a-file').text = 'with some text'

        makeTaskUpToDate('checkCreated')

        when:
        targetFile.file('a-file').text = 'with some different text'

        then:
        assertTaskIsOutOfDate("checkCreated")
    }

    def "disable caching if broken symbolic link is found"() {
        given:
        makeScenarioProject()
        createCleanTask()
        makeTaskCacheable('checkCreated')

        expect:
        executer.withStackTraceChecksDisabled()
        withBuildCache().succeeds('checkCreated')
        executedAndNotSkipped(':checkCreated')

        executer.withArgument("-i").withStackTraceChecksDisabled()
        withBuildCache().succeeds('clean', 'checkCreated')
        executedAndNotSkipped(':checkCreated')

        outputContains(cachingDisabledMessage)
    }

    abstract void makeScenarioProject()

    void createCleanTask() {
        buildFile << """
            task clean {
                doLast {
                    file('${outputFileToClean.absolutePath}').delete()
                }
            }
        """
    }

    abstract TestFile getSymbolicLinkUnderTest()

    abstract TestFile getTargetFile()

    String getRelativeTarget() {
        return symbolicLinkUnderTest.parentFile.toPath().relativize(targetFile.toPath()).toString()
    }

    abstract TestFile getAlternateTargetFile()

    abstract TestFile getOutputFileToClean()

    abstract String getCachingDisabledMessage()

    String getRelativeAlternateTarget() {
        return symbolicLinkUnderTest.parentFile.toPath().relativize(alternateTargetFile.toPath()).toString()
    }

    void assertTaskIsOutOfDate(String taskName) {
        succeeds(taskName)
        result.assertTaskNotSkipped(":$taskName")
    }

    void makeTaskUpToDate(String taskName) {
        succeeds(taskName)
        result.assertTaskNotSkipped(":$taskName")

        succeeds(taskName)
        result.assertTasksSkipped(":$taskName")
    }

    void makeTaskCacheable(String taskName) {
        buildFile << "checkCreated.outputs.cacheIf { true }"
    }

    void changeSymbolicLinkTarget(TestFile symbolicLink, TestFile newTarget) {
        symbolicLink.assertIsLink()

//        def lastModifiedTime = Files.getLastModifiedTime(symbolicLink.toPath(), LinkOption.NOFOLLOW_LINKS)
        assert symbolicLink.delete()

        Files.createSymbolicLink(symbolicLink.toPath(), newTarget.toPath())
//        symbolicLink.setLastModified(lastModifiedTime.toMillis())
    }
}
