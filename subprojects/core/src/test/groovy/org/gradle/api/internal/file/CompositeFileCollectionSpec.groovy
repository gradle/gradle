/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.internal.file

import org.gradle.api.Task
import org.gradle.api.internal.file.collections.FileCollectionResolveContext
import org.gradle.api.internal.file.collections.SimpleFileCollection
import org.gradle.util.UsesNativeServices
import spock.lang.Specification

@UsesNativeServices
class CompositeFileCollectionSpec extends Specification {
    def "visits contents on each query"() {
        def visited = 0;
        def collection = new TestCollection() {
            @Override
            void resolve(FileCollectionResolveContext context) {
                visited++
                context.add(new SimpleFileCollection([new File("foo")]))
            }
        }

        when:
        def sourceCollections = collection.sourceCollections

        then:
        sourceCollections.size() == 1
        visited == 1

        when:
        def files = collection.files

        then:
        files.size() == 1
        visited == 2
    }

    def "visits contents when task dependencies are queried"() {
        def visited = 0;
        def collection = new TestCollection() {
            @Override
            void resolve(FileCollectionResolveContext context) {
                visited++
            }
        }

        when:
        def dependencies = collection.buildDependencies

        then:
        visited == 0

        when:
        dependencies.getDependencies(Stub(Task))

        then:
        visited == 1

        when:
        dependencies.getDependencies(Stub(Task))

        then:
        visited == 2
    }

    private static abstract class TestCollection extends CompositeFileCollection {
        @Override
        String getDisplayName() {
            return "<display-name>"
        }
    }
}
