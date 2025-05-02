/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.api.internal.ConventionTask
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.util.internal.WrapUtil

import static org.gradle.api.internal.file.TestFiles.fileSystem

class DeleteTest extends AbstractConventionTaskTest {
    private Delete delete

    def setup() {
        delete = createTask(Delete)
    }

    ConventionTask getTask() {
        return delete
    }

    def "deletes nothing by default"() {
        expect:
        delete.getDelete().isEmpty()
    }

    def "did work is true when something gets deleted"() {
        given:
        def file = temporaryFolder.createFile("someFile")

        when:
        delete.delete(file)
        execute(delete)

        then:
        delete.getDidWork()
        !file.exists()
    }

    def "did work is false when nothing gets deleted"() {
        when:
        delete.delete("does-not-exist")
        execute(delete)

        then:
        !delete.getDidWork()
    }

    def "get target files and multiple targets"() {
        when:
        delete.delete("someFile")
        delete.delete(new File("someOtherFile"))
        delete.getTargetFiles()

        then:
        delete.getDelete() == WrapUtil.toSet("someFile", new File("someOtherFile"))
        delete.getTargetFiles().getFiles() == getProject().files(delete.getDelete()).getFiles()
    }

    @Requires(UnitTestPreconditions.Symlinks)
    def "can follow symlinks"() {
        given:
        def keepTxt = temporaryFolder.createFile("originalDir", "keep.txt")
        def originalDir = keepTxt.getParentFile()
        def link = new File(temporaryFolder.getTestDirectory(), "link")
        fileSystem().createSymbolicLink(link, originalDir)

        when:
        delete.delete(link)
        delete.setFollowSymlinks(true)
        execute(delete)

        then:
        delete.getDidWork()
        !link.exists()
        !keepTxt.exists()
    }

    @Requires(UnitTestPreconditions.Symlinks)
    def "will not follow symlinks by default"() {
        given:
        def keepTxt = temporaryFolder.createFile("originalDir", "keep.txt")
        def originalDir = keepTxt.getParentFile()
        def link = new File(temporaryFolder.getTestDirectory(), "link")
        fileSystem().createSymbolicLink(link, originalDir)

        when:
        delete.delete(link)
        execute(delete)

        then:
        delete.getDidWork()
        !link.exists()
        keepTxt.exists()
    }
}
