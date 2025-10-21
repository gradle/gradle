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

package org.gradle.buildinit.plugins.internal

import org.gradle.api.tasks.wrapper.Wrapper
import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class WrapperOptionsIntegrationTest extends AbstractIntegrationSpec {

    def "shows available distribution type option values"() {
        when:
        run "help", "--task", "wrapper"

        then:
        def options = Wrapper.DistributionType.values().sort { it.toString() }.join(" ")
        output.replaceAll(/\s+/, " ")
            .contains("""--distribution-type The type of the Gradle distribution to be used by the wrapper. Available values are: ${options}""")
    }
}
