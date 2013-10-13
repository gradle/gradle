/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.tasks

import org.gradle.api.Action
import org.gradle.api.file.FileCopyDetails
import org.gradle.api.internal.DynamicObject
import org.gradle.test.fixtures.file.TestFile
import org.junit.Test
import spock.lang.Issue
import spock.lang.Unroll

import static junit.framework.TestCase.fail

/**
 * Tests that different types of copy tasks correctly expose DSL enhanced objects.
 */
abstract class AbstractCopyTaskContractTest extends AbstractConventionTaskTest {

    abstract AbstractCopyTask getTask()

    @Unroll
    @Test
    public void rootLevelFileCopyDetailsIsDslEnhanced() {
        task.eachFile {
            assert delegate instanceof DynamicObject
        }
        task.eachFile(new Action<FileCopyDetails>() {
            void execute(FileCopyDetails fcd) {
                assert fcd instanceof DynamicObject
            }
        })
    }

    @Test
    @Issue("GRADLE-2906")
    void "each file does not execute action for directories"() {
        File fromSrcDir = createDir(project.projectDir, 'src')
        File fromConfDir = createDir(fromSrcDir, 'conf')
        File fromPropertiesFile = createFile(fromConfDir, 'file.properties')
        fromPropertiesFile.text << 'foo'
        File intoBuildDir = createDir(project.projectDir, 'build')
        EachFileClosureInvocation closureInvocation = new EachFileClosureInvocation()

        task.from fromSrcDir
        task.into intoBuildDir
        task.eachFile closureInvocation.closure
        task.execute()

        assert closureInvocation.wasCalled(1)
        assert closureInvocation.files.containsAll(fromPropertiesFile)
    }

    @Test
    @Issue("GRADLE-2900")
    void "each file does not execute action for directories after filtering file tree"() {
        File fromSrcDir = createDir(project.projectDir, 'src')
        File fromConfDir = createDir(fromSrcDir, 'conf')
        File fromPropertiesFile = createFile(fromConfDir, 'file.properties')
        fromPropertiesFile.text << 'foo'
        File intoBuildDir = createDir(project.projectDir, 'build')
        EachFileClosureInvocation closureInvocation = new EachFileClosureInvocation()

        task.from(project.fileTree(dir: fromSrcDir).matching { include 'conf/file.properties' }) { eachFile closureInvocation.closure }
        task.into intoBuildDir
        task.execute()

        assert closureInvocation.wasCalled(1)
        assert closureInvocation.files.contains(fromPropertiesFile)
    }

    private File createDir(File parentDir, String path) {
        TestFile newDir = new TestFile(parentDir, path)
        boolean success = newDir.mkdirs()

        if(!success) {
            fail "Failed to create directory $newDir"
        }

        newDir
    }

    private File createFile(File parent, String filename) {
        TestFile file = new TestFile(parent, filename)
        file.createNewFile()
        file
    }

    private class EachFileClosureInvocation {
        private int calls = 0
        private final Closure closure
        private final List<String> files = []

        public EachFileClosureInvocation() {
            closure = {
                ++calls
                files << it.file
            }
        }

        Closure getClosure() {
            closure
        }

        boolean wasCalled(int times) {
            calls == times
        }

        List<String> getFiles() {
            files
        }
    }
}
