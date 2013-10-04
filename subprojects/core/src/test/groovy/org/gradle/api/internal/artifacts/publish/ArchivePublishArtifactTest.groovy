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

import org.gradle.api.tasks.bundling.Jar
import org.gradle.util.TestUtil
import spock.lang.Specification

public class ArchivePublishArtifactTest extends Specification {

    def "provides sensible default values for quite empty archive tasks"() {
        def quiteEmptyJar = TestUtil.createTask(Jar)

        when:
        def a = new ArchivePublishArtifact(quiteEmptyJar)

        then:
        a.archiveTask == quiteEmptyJar
        a.classifier == ""
        a.date.time == quiteEmptyJar.archivePath.lastModified()
        a.extension == "jar"
        a.file == quiteEmptyJar.archivePath
        a.type == "jar"
    }

    def "configures name correctly"() {
        def noName = TestUtil.createTask(Jar)
        def withBaseName = TestUtil.createTask(Jar, [baseName: "foo"])
        def withAppendix = TestUtil.createTask(Jar, [baseName: "foo", appendix: "javadoc"])
        def withAppendixOnly = TestUtil.createTask(Jar, [appendix: "javadoc"])

        expect:
        new ArchivePublishArtifact(noName).name == "null" //juck!
        new ArchivePublishArtifact(withBaseName).name == "foo"
        new ArchivePublishArtifact(withBaseName).setName("haha").name == "haha"
        new ArchivePublishArtifact(withAppendix).name == "foo-javadoc"
        new ArchivePublishArtifact(withAppendixOnly).name == "null-javadoc"
    }
}
