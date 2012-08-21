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

package org.gradle.api.plugins.migration.gradle.internal

import org.gradle.api.internal.filestore.DefaultFileStoreEntry
import org.gradle.api.internal.filestore.FileStore
import org.gradle.api.internal.filestore.FileStoreEntry
import org.gradle.api.plugins.migration.fixtures.gradle.ProjectOutputBuilder
import org.gradle.api.plugins.migration.model.outcome.internal.archive.GeneratedArchiveBuildOutcome
import org.gradle.tooling.model.internal.migration.ProjectOutput
import spock.lang.Specification

class GradleBuildOutcomeSetTransformerTest extends Specification {

    def store = new FileStore<String>() {
        FileStoreEntry move(String key, File source) {
            new DefaultFileStoreEntry(source)
        }

        FileStoreEntry copy(String key, File source) {
            new DefaultFileStoreEntry(source)
        }

        File getTempFile() {
            throw new UnsupportedOperationException()
        }
    }

    def transformer = new GradleBuildOutcomeSetTransformer(store)

    def "can transform"() {
        given:
        ProjectOutputBuilder builder = new ProjectOutputBuilder()
        ProjectOutput projectOutput = builder.build {
            createChild("a") {
                addArchive "a1"
            }
            createChild("b") {
                addArchive "b1"
                addArchive "b2"
            }
            createChild("c") {
                createChild("a") {
                    addArchive "ca1"
                    addArchive "ca2"
                }
            }
            createChild("d")
        }

        when:
        def outcomes = transformer.transform(projectOutput)

        then:
        outcomes.size() == 5
        outcomes*.name.toList().sort() == [":a:a1", ":b:b1", ":b:b2", ":c:a:ca1", ":c:a:ca2"]
        outcomes.every { it instanceof GeneratedArchiveBuildOutcome }
    }

}

