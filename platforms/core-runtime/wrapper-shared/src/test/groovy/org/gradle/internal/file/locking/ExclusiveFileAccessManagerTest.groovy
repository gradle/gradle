/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.file.locking

import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class ExclusiveFileAccessManagerTest extends Specification {
    @Rule
    public TestNameTestDirectoryProvider temporaryDirectory = new TestNameTestDirectoryProvider(getClass())
    private manager = new ExclusiveFileAccessManager(1000, 10)

    def 'If the directory for the lock file cannot be created then we get a good error message'() {
        given:
        def fileWithSameNameAsDirectory = temporaryDirectory.createFile('someDir')
        when:
        manager.access(fileWithSameNameAsDirectory.file('someFile.zip')) {
        }

        then:
        RuntimeException e = thrown()
        e.message == "Could not create parent directory for lock file ${fileWithSameNameAsDirectory.file('someFile.zip.lck').absolutePath}"
    }
}
