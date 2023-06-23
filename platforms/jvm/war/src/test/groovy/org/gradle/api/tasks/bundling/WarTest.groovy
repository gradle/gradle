/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.tasks.bundling

import org.gradle.api.Action
import org.gradle.api.file.CopySpec
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.test.fixtures.archive.JarTestFixture

class WarTest extends AbstractArchiveTaskTest {
    War war

    def setup() {
        war = createTask(War)
        configure(war)
    }

    @Override
    AbstractArchiveTask getArchiveTask() {
        war
    }

    def "test War"() {
        expect:
        war.archiveExtension.get() == War.WAR_EXTENSION
    }

    def "can configure WEB-INF CopySpec using an Action"() {
        given:
        war.webInf({ CopySpec spec ->
            spec.from temporaryFolder.createFile('file.txt')
        } as Action<CopySpec>)

        when:
        execute(war)

        then:
        new JarTestFixture(war.archiveFile.get().asFile).assertContainsFile('WEB-INF/file.txt')
    }

    def "configures destinationDirectory for war tasks"() {
        when:
        project.pluginManager.apply(JavaBasePlugin)
        project.version = '1.0'

        then:
        def someWar = project.tasks.create('someWar', War)
        someWar.destinationDirectory.get().asFile == project.libsDirectory.get().asFile
    }
}
