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

import org.gradle.api.internal.file.copy.CopyAction
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification

abstract class AbstractArchivePublishArtifactTest extends Specification {
    @Rule
    public TestNameTestDirectoryProvider temporaryFolder = TestNameTestDirectoryProvider.newInstance()

    def TestUtil testUtil = TestUtil.create(temporaryFolder)

    abstract AbstractArchivePublishArtifact archiveArtifact(Object taskOrProvider)

    abstract Object taskOrProvider(Class<?> type, Map attributes = [:])

    abstract AbstractArchiveTask taskFrom(Object taskOrProvider)

    def "provides sensible default values for quite empty archive tasks"() {
        def quiteEmptyJar = taskOrProvider(DummyJar)

        when:
        def a = archiveArtifact(quiteEmptyJar)

        then:
        a.archiveTask == taskFrom(quiteEmptyJar)
        a.classifier == ""
        a.date.time == taskFrom(quiteEmptyJar).archivePath.lastModified()
        a.extension == "jar"
        a.file == taskFrom(quiteEmptyJar).archivePath
        a.type == "jar"
    }

    def "configures name correctly"() {
        def noName = taskOrProvider(DummyJar)
        def withArchiveName = taskOrProvider(DummyJar, [archiveName: "hey"])
        def withBaseName = taskOrProvider(DummyJar, [baseName: "foo"])
        def withAppendix = taskOrProvider(DummyJar, [baseName: "foo", appendix: "javadoc"])
        def withAppendixOnly = taskOrProvider(DummyJar, [appendix: "javadoc"])

        expect:
        archiveArtifact(noName).name == null
        archiveArtifact(withArchiveName).name == null
        def baseNameArtifact = archiveArtifact(withBaseName)
        baseNameArtifact.name == "foo"
        baseNameArtifact.setName("haha")
        baseNameArtifact.name == "haha"
        archiveArtifact(withAppendix).name == "foo-javadoc"
        archiveArtifact(withAppendixOnly).name == "javadoc"
    }

    static class DummyJar extends AbstractArchiveTask {
        DummyJar() {
            extension = "jar"
        }

        protected CopyAction createCopyAction() {
            return null
        }
    }
}
