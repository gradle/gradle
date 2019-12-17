/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.vfs

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

import static org.gradle.internal.service.scopes.VirtualFileSystemServices.VFS_PARTIAL_INVALIDATION_ENABLED_PROPERTY

class VirtualFileSystemPartialInvalidationIntegrationTest extends AbstractIntegrationSpec {

    private static final INCUBATING_MESSAGE = "Partial virtual file system invalidation is an incubating feature"

    def setup() {
        executer.beforeExecute {
            // Don't enable partial vfs invalidation by default,
            // the test cases enable and disable on their own.
            withPartialVfsInvalidation(false)
        }
    }

    def "incubating message is shown for partial invalidation"() {
        buildFile << """
            apply plugin: "java"
        """

        when:
        run "assemble", "-D${VFS_PARTIAL_INVALIDATION_ENABLED_PROPERTY}"
        then:
        outputContains(INCUBATING_MESSAGE)

        when:
        run "assemble"
        then:
        outputDoesNotContain(INCUBATING_MESSAGE)
    }

    def "incubating message is shown when partial invalidation is enabled via gradle.properties"() {
        buildFile << """
            apply plugin: "java"
        """
        file("gradle.properties") << """systemProp.${VFS_PARTIAL_INVALIDATION_ENABLED_PROPERTY}=true"""

        when:
        run "assemble"
        then:
        outputContains(INCUBATING_MESSAGE)
    }
}
