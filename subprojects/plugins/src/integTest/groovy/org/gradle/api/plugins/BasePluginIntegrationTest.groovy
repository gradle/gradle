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
package org.gradle.api.plugins

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

class BasePluginIntegrationTest extends AbstractIntegrationSpec {

    @Requires(TestPrecondition.MANDATORY_FILE_LOCKING)
    def "clean failure message indicates file"() {
        given:
        executer.withForkingExecuter()
        buildFile << """
            apply plugin: 'base'
        """

        and:
        def lock = new RandomAccessFile(file("build/newFile").createFile(), "rw").channel.lock()

        when:
        fails "clean"

        then:
        failure.assertHasCause("Unable to delete file")

        cleanup:
        lock?.release()
    }

}
