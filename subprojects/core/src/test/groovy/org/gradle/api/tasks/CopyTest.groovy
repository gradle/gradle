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

import org.apache.commons.io.FileUtils
import org.gradle.test.fixtures.file.TestFile
import org.junit.Before
import org.junit.Test
import spock.lang.Issue

import static junit.framework.TestCase.fail

class CopyTest extends AbstractCopyTaskContractTest {
    private Copy copy

    AbstractCopyTask getTask() {
        return copy
    }

    @Before
    public void setUp() {
        copy = createTask(Copy.class)
    }

    @Test
    @Issue("GRADLE-2906")
    void "each file does not execute action for directories"() {
        File fromSrcDir = createDir(project.projectDir, 'src')
        File fromConfDir = createDir(fromSrcDir, 'conf')
        File fromPropertiesFile = createFile(fromConfDir, 'file.properties')
        fromPropertiesFile.text << 'foo'
        File intoBuildDir = createDir(project.projectDir, 'build')
        def closure = { assert it.file.canonicalPath == fromPropertiesFile.canonicalPath }
        EachFileClosureInvocation closureInvocation = new EachFileClosureInvocation(closure)

        copy.from fromSrcDir
        copy.into intoBuildDir
        copy.eachFile closureInvocation.closure
        copy.execute()

        File intoConfDir = new File(intoBuildDir, 'conf')
        File intoPropertiesFile = new File(intoConfDir, 'file.properties')
        assert intoPropertiesFile.exists()
        assert FileUtils.contentEquals(intoPropertiesFile, fromPropertiesFile)
        assert closureInvocation.wasCalled(1)
    }

    @Test
    @Issue("GRADLE-2900")
    void "each file does not execute action for directories after filtering file tree"() {
        File fromSrcDir = createDir(project.projectDir, 'src')
        File fromConfDir = createDir(fromSrcDir, 'conf')
        File fromPropertiesFile = createFile(fromConfDir, 'file.properties')
        fromPropertiesFile.text << 'foo'
        File intoBuildDir = createDir(project.projectDir, 'build')
        def closure = { assert it.file.canonicalPath == fromPropertiesFile.canonicalPath }
        EachFileClosureInvocation closureInvocation = new EachFileClosureInvocation(closure)

        copy.from(project.fileTree(dir: fromSrcDir).matching { include 'conf/file.properties' }) { eachFile closureInvocation.closure }
        copy.into intoBuildDir
        copy.execute()

        File intoConfDir = new File(intoBuildDir, 'conf')
        File intoPropertiesFile = new File(intoConfDir, 'file.properties')
        assert intoPropertiesFile.exists()
        assert FileUtils.contentEquals(intoPropertiesFile, fromPropertiesFile)
        assert closureInvocation.wasCalled(1)
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

        public EachFileClosureInvocation(Closure delegateClosure) {
            closure = {
                ++calls
                delegateClosure(it)
            }
        }

        Closure getClosure() {
            closure
        }

        int getCalls() {
            calls
        }

        boolean wasCalled(int times) {
            calls == times
        }
    }
}
