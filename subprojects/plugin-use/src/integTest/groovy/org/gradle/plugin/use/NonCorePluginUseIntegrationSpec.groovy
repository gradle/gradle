/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.plugin.use

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class NonCorePluginUseIntegrationSpec extends AbstractIntegrationSpec {

    def "non core plugin without version produces error message"() {
        given:
        buildScript """
            plugins {
                id "foo.bar"
            }
        """

        when:
        fails "tasks"

        then:
        failure.assertHasDescription("""Plugin [id 'foo.bar'] was not found in any of the following sources:

- Gradle Core Plugins (plugin is not in 'org.gradle' namespace)
- Script Plugins (only script plugin requests are supported by this source)
- Gradle Central Plugin Repository (plugin dependency must include a version number for this source)""")
    }

}
