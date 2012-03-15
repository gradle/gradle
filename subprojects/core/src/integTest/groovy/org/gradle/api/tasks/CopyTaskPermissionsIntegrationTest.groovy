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

package org.gradle.api.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Ignore
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

@Ignore
@Requires(TestPrecondition.FILE_PERMISSIONS)
class CopyTaskPermissionsIntegrationTest extends AbstractIntegrationSpec {
    def "file permission set fileMode overwrites existing permissions"() {
        given:
        file("reference.txt") << 'test file"'

        and:
        buildFile << """
        import static java.lang.Integer.toOctalString
        task copy(type: Copy) {
            from "reference.txt"
            into ("build/tmp")
            fileMode = $mode
        }

        task verifyPermissions(dependsOn: copy) << {
            fileTree("build/tmp").visit{
                assert toOctalString($mode) == toOctalString(it.mode)
            }
        }
        """

        when:
        run "verifyPermissions"

        then:
        noExceptionThrown()
        
        where:
        mode << [0644, 0755, 0777]

    }
}
