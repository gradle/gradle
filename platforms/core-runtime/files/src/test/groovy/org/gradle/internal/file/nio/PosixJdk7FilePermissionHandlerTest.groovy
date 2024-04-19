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

package org.gradle.internal.file.nio

import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.junit.Rule
import spock.lang.Specification

@Requires(UnitTestPreconditions.NotWindows)
class PosixJdk7FilePermissionHandlerTest extends Specification {
    @Rule TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    def "test chmod on non windows platforms with JDK7"() {
        setup:
        def file = temporaryFolder.createFile("testFile")
        def handler = new PosixJdk7FilePermissionHandler()
        when:
        handler.chmod(file, mode);
        then:
        mode == handler.getUnixMode(file);
        where:
        mode << [0722, 0644, 0744, 0755]
    }

}
