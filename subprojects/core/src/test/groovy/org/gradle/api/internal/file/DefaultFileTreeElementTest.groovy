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
package org.gradle.api.internal.file

import org.gradle.api.file.FileTreeElement
import org.gradle.internal.nativeplatform.filesystem.FileSystems
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Rule
import spock.lang.Specification

class DefaultFileTreeElementTest extends Specification {
    @Rule TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()

    @Requires(TestPrecondition.FILE_PERMISSIONS)
    def "permissions on file can be read"() {
        def f = tmpDir.createFile("f")
        FileTreeElement e = new DefaultFileTreeElement(f, null)

        when:
        FileSystems.default.chmod(f, 0644)

        then:
        e.mode == 0644
    }
}
