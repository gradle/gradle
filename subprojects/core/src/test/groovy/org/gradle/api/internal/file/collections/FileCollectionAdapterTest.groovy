/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.file.collections

import spock.lang.Specification

class FileCollectionAdapterTest extends Specification {
    MinimalFileCollection collection = Mock()
    FileCollectionAdapter adapter = new FileCollectionAdapter(collection)

    def delegatesToTargetCollectionToBuildSetOfFiles() {
        def expectedFiles = [new File('a'), new File('b')]

        when:
        def files = adapter.files

        then:
        files == (expectedFiles as LinkedHashSet)
        1 * collection.iterator() >> expectedFiles.iterator()
        0 * _._
    }

    def resolveAddsTargetCollectionToContext() {
        FileCollectionResolveContext context = Mock()

        when:
        adapter.resolve(context)

        then:
        1 * context.add(collection)
        0 * _._
    }
}
