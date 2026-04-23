/*
 * Copyright 2025 the original author or authors.
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

package gradlebuild.buildutils.tasks

import spock.lang.Specification

class PreparePatchReleaseTest extends Specification {

    def "patch version is correctly bumped"() {
        expect:
        ReleasedVersionsHelperKt.bumpPatchVersion(input) == expected

        where:
        input   | expected
        "9.4.0" | "9.4.1"
        "9.4.1" | "9.4.2"
        "8.0.0" | "8.0.1"
        "1.2.3" | "1.2.4"
    }

    def "invalid version format is rejected"() {
        when:
        ReleasedVersionsHelperKt.bumpPatchVersion(version)

        then:
        thrown(IllegalArgumentException)

        where:
        version << ["9.4", "9", "9.4.0.1", "not.a.version", "9.4.0-SNAPSHOT"]
    }
}