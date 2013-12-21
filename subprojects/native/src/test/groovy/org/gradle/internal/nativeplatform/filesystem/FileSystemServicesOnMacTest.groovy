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
package org.gradle.internal.nativeplatform.filesystem

import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Rule
import spock.lang.Specification

@Requires(TestPrecondition.MAC_OS_X)
public class FileSystemServicesOnMacTest extends Specification {
    @Rule TestNameTestDirectoryProvider temporaryFolder
    final Chmod chmod = FileSystemServices.services.get(Chmod)
    final Stat stat = FileSystemServices.services.get(Stat)
    final Symlink symlink = FileSystemServices.services.get(Symlink)

    def "creates LibCChmod on Mac"() {
        expect:
        chmod instanceof LibcChmod
    }

    def "creates LibCStat on Mac"() {
        expect:
        stat instanceof LibCStat
    }

    def "creates LibcSymlink on Mac"() {
        expect:
        symlink instanceof LibcSymlink
    }
}
