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

class UpdateAgpVersionsTest extends Specification {

    def "selects the right versions"() {
        given:
        def minimumSupported = "8.8.0"
        def allVersions = [
            "8.7.0-alpha01", "8.7.0-beta01", "8.7.0-rc01", "8.7.0", "8.7.1",
            "8.8.0-alpha01", "8.8.0-beta01", "8.8.0-rc01", "8.8.0", "8.8.1",
            "8.9.0-alpha01", "8.9.0-beta01", "8.9.0-rc01", "8.9.0", "8.9.1",
            "8.10.0-alpha01", "8.10.0-beta01", "8.10.0-rc01", "8.10.0", "8.10.1",
            "8.11.0-alpha01", "8.11.0-beta01", "8.11.0-rc01",
            "8.12.0-alpha01", "8.12.0-beta01",
            "8.13.0-alpha01",
        ].shuffled()

        when:
        def selected = UpdateAgpVersions.selectVersionsFrom(minimumSupported, allVersions)

        then:
        selected == ["8.8.1", "8.9.1", "8.10.1", "8.11.0-rc01", "8.12.0-beta01", "8.13.0-alpha01"]
    }
}
