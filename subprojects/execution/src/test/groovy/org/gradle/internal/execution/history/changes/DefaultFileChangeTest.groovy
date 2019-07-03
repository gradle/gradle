/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.execution.history.changes

import org.gradle.internal.file.FingerprintFileType
import spock.lang.Specification
import spock.lang.Unroll

@Unroll
class DefaultFileChangeTest extends Specification {

    def "change message for ChangeType MODIFIED from #previous to #current is '#message'"() {
        expect:
        DefaultFileChange.modified("somePath", "test", previous, current, "").message == "test file somePath ${message}."

        where:
        previous                        | current                         | message
        FingerprintFileType.RegularFile | FingerprintFileType.RegularFile | "has changed"
        FingerprintFileType.Missing     | FingerprintFileType.RegularFile | "has been added"
        FingerprintFileType.Missing     | FingerprintFileType.Directory   | "has been added"
        FingerprintFileType.RegularFile | FingerprintFileType.Missing     | "has been removed"
        FingerprintFileType.Directory   | FingerprintFileType.Missing     | "has been removed"
    }

    def "change message for ChangeType #fileChange.change is '#message'"() {
        expect:
        fileChange.message == "test file somePath ${message}."

        where:
        fileChange                                                                         | message
        DefaultFileChange.removed("somePath", "test", FingerprintFileType.RegularFile, "") | "has been removed"
        DefaultFileChange.removed("somePath", "test", FingerprintFileType.Directory, "")   | "has been removed"
        DefaultFileChange.added("somePath", "test", FingerprintFileType.RegularFile, "")   | "has been added"
        DefaultFileChange.added("somePath", "test", FingerprintFileType.Directory, "")     | "has been added"
    }
}
