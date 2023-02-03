/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.jvm.tasks

import org.gradle.api.Action
import org.gradle.api.file.CopySpec
import org.gradle.api.java.archives.Manifest
import org.gradle.api.java.archives.internal.DefaultManifest
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.bundling.AbstractArchiveTaskTest
import org.gradle.test.fixtures.archive.JarTestFixture

class JarTest extends AbstractArchiveTaskTest {
    Jar jar

    def setup() {
        jar = createTask(Jar)
        configure(jar)
    }

    @Override
    AbstractArchiveTask getArchiveTask() {
        jar
    }

    def "test Jar"() {
        expect:
        jar.archiveExtension.get() == Jar.DEFAULT_EXTENSION
        jar.manifest != null
        jar.metaInf != null
    }

    def "correct jar manifest"() {
        when:
        jar.manifest = new DefaultManifest(null)
        jar.manifest {
            attributes(key: 'value')
        }

        then:
        jar.manifest.attributes.key == 'value'
    }

    def "correct jar manifest from null manifest"() {
        when:
        jar.manifest = null
        jar.manifest {
            attributes(key: 'value')
        }

        then:
        jar.manifest.attributes.key == 'value'
    }

    def "can configure manifest using an Action"() {
        when:
        jar.manifest({ Manifest manifest ->
            manifest.attributes(key: 'value')
        } as Action<Manifest>)

        then:
        jar.manifest.attributes.key == 'value'
    }

    def "can configure META-INF CopySpec using an Action"() {
        given:
        jar.metaInf({ CopySpec spec ->
            spec.from temporaryFolder.createFile('file.txt')
        } as Action<CopySpec>)

        when:
        execute(jar)

        then:
        new JarTestFixture(jar.archiveFile.get().asFile).assertContainsFile('META-INF/file.txt')
    }
}
