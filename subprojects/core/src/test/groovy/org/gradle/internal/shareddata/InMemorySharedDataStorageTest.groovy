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

package org.gradle.internal.shareddata

import org.gradle.api.provider.Provider
import org.gradle.util.Path
import spock.lang.Specification

class InMemorySharedDataStorageTest extends Specification {
    def instance = new InMemorySharedDataStorage()

    def 'distinguishes data by #kind'() {
        given:
        def consumerPath = Path.ROOT
        def dataProvider = Mock(Provider)

        and:
        instance.put(Path.path(putPath), putKeyType, putKeyIdentifier, dataProvider)

        when:
        def resolver = instance.getProjectDataResolver(consumerPath, Path.path(putPath))
        def present = resolver.get(new SharedDataStorage.DataKey(putKeyType, putKeyIdentifier))

        then:
        present == dataProvider

        when:
        def resolverWithMissingKey = instance.getProjectDataResolver(consumerPath, Path.path(missingGetPath))
        def missing = resolverWithMissingKey.get(new SharedDataStorage.DataKey(missingGetType, missingGetIdentifier))

        then:
        missing == null

        where:
        kind                      | putKeyType | putKeyIdentifier | putPath | missingGetType | missingGetIdentifier | missingGetPath
        'type'                    | String     | "test"           | ':'     | Integer        | "test"               | ':'
        'type with no identifier' | String     | null             | ':'     | Integer        | null                 | ':'
        'identifier'              | String     | "test"           | ':'     | String         | "other"              | ':'
        'project path'            | String     | "test"           | ':a'    | String         | "test"               | ':b'
    }

    def 'returns resolver that is live'() {
        given:
        def consumerPath = Path.path(":consumer")
        def projectPath = Path.path(":")
        def dataProvider1 = Mock(Provider)
        def dataProvider2 = Mock(Provider)

        when:
        instance.put(projectPath, String, "test1", dataProvider1)
        def resolver = instance.getProjectDataResolver(consumerPath, projectPath)
        def data1 = resolver.get(new SharedDataStorage.DataKey(String, "test1"))
        instance.put(projectPath, String, "test2", dataProvider2)
        def data2 = resolver.get(new SharedDataStorage.DataKey(String, "test2"))

        then:
        data1 === dataProvider1
        data2 === dataProvider2
    }
}
