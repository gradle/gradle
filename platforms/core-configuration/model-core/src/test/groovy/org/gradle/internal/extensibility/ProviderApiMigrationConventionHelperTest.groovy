/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.extensibility

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ReplacedBy
import spock.lang.Specification

class ProviderApiMigrationConventionHelperTest extends Specification {

    def "resolves #type.simpleName property '#property' to #expected"() {
        expect:
        ProviderApiMigrationConventionHelper.findRenamedProperty(type, property) == expected

        where:
        type                    | property  || expected
        LazyReplacement         | "oldFile" || "newFile"
        LazyReplacementSubclass | "oldFile" || "newFile"
        LazyReplacementImpl     | "oldFile" || "newFile"
        LazyReplacement         | "newFile" || null
        EagerReplacement        | "oldFile" || null
        MissingReplacement      | "oldFile" || null
    }

    static class LazyReplacement {
        @ReplacedBy("newFile")
        File getOldFile() { null }

        RegularFileProperty getNewFile() { null }
    }

    static class LazyReplacementSubclass extends LazyReplacement {
    }

    interface LazyReplacementInterface {
        @ReplacedBy("newFile")
        File getOldFile()

        RegularFileProperty getNewFile()
    }

    static class LazyReplacementImpl implements LazyReplacementInterface {
        @Override
        File getOldFile() { null }

        @Override
        RegularFileProperty getNewFile() { null }
    }

    static class EagerReplacement {
        @ReplacedBy("newFile")
        File getOldFile() { null }

        File getNewFile() { null }
    }

    static class MissingReplacement {
        @ReplacedBy("newFile")
        File getOldFile() { null }
    }
}
