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
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.ProjectBuilderTestUtil
import org.junit.Rule
import spock.lang.Specification

class ArchivePublishArtifactTest extends Specification {
    @Rule
    public TestNameTestDirectoryProvider temporaryFolder = TestNameTestDirectoryProvider.newInstance(getClass())

    def "provides sensible default values for quite empty archive tasks"() {
        ProjectInternal project = ProjectBuilderTestUtil.createRootProject(temporaryFolder)
        def quiteEmptyJar = project.tasks.create("name", DummyJar)
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
        ProjectInternal project = ProjectBuilderTestUtil.createRootProject(temporaryFolder)
        def noName = project.tasks.create("one", DummyJar)
        def withArchiveName = project.tasks.create("two", DummyJar)
        withArchiveName.archiveFileName.set("hey")
        def withBaseName = project.tasks.create("three", DummyJar)
        withBaseName.archiveBaseName.set("foo")
        def withAppendix = project.tasks.create("four", DummyJar)
        withAppendix.archiveBaseName.set("foo")
        withAppendix.archiveAppendix.set("javadoc")
        def withAppendixOnly = project.tasks.create("five", DummyJar)
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

    static abstract class DummyJar extends AbstractArchiveTask {
        DummyJar() {
            archiveExtension.set("jar")
        }

        protected CopyAction createCopyAction() {
            return null
        }
    }
}
