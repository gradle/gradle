/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.api.internal.artifacts.publish

import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.file.copy.CopyAction
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification

class ArchivePublishArtifactTest extends Specification {
    @Rule
    public TestNameTestDirectoryProvider temporaryFolder = TestNameTestDirectoryProvider.newInstance(getClass())

    def testUtil = TestUtil.create(temporaryFolder)

    def "provides sensible default values for quite empty archive tasks"() {
        def quiteEmptyJar = testUtil.task(DummyJar)
        quiteEmptyJar.destinationDirectory.set(temporaryFolder.testDirectory)

        when:
        def a = new ArchivePublishArtifact(TestFiles.taskDependencyFactory(), quiteEmptyJar)

        then:
        a.archiveTask == quiteEmptyJar
        a.classifier == ""
        a.date.time == quiteEmptyJar.archiveFile.get().asFile.lastModified()
        a.extension == "jar"
        a.file == quiteEmptyJar.archiveFile.get().asFile
        a.type == "jar"
    }

    def "configures name correctly"() {
        def noName = testUtil.task(DummyJar)
        def withArchiveName = testUtil.task(DummyJar)
        withArchiveName.archiveFileName.set("hey")
        def withBaseName = testUtil.task(DummyJar)
        withBaseName.archiveBaseName.set("foo")
        def withAppendix = testUtil.task(DummyJar)
        withAppendix.archiveBaseName.set("foo")
        withAppendix.archiveAppendix.set("javadoc")
        def withAppendixOnly = testUtil.task(DummyJar)
        withAppendixOnly.archiveAppendix.set("javadoc")
        def taskDependencyFactory = TestFiles.taskDependencyFactory()

        expect:
        new ArchivePublishArtifact(taskDependencyFactory, noName).name == null
        new ArchivePublishArtifact(taskDependencyFactory, withArchiveName).name == null
        def baseNameArtifact = new ArchivePublishArtifact(taskDependencyFactory, withBaseName)
        baseNameArtifact.name == "foo"
        baseNameArtifact.setName("haha")
        baseNameArtifact.name == "haha"
        new ArchivePublishArtifact(taskDependencyFactory, withAppendix).name == "foo-javadoc"
        new ArchivePublishArtifact(taskDependencyFactory, withAppendixOnly).name == "javadoc"
    }

    static class DummyJar extends AbstractArchiveTask {
        DummyJar() {
            archiveExtension.set("jar")
        }

        protected CopyAction createCopyAction() {
            return null
        }
    }
}
