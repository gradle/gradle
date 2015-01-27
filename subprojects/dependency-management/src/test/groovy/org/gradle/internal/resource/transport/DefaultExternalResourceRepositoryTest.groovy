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

package org.gradle.internal.resource.transport

import org.gradle.internal.resource.transfer.ExternalResourceAccessor
import org.gradle.internal.resource.transfer.ExternalResourceLister
import org.gradle.internal.resource.transfer.ExternalResourceUploader
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class DefaultExternalResourceRepositoryTest extends Specification {
    @Rule
    final TestNameTestDirectoryProvider testDir = new TestNameTestDirectoryProvider()

    def "should not put a checksum if the intended destination already is a checksum"() {
        ExternalResourceAccessor accessor = Mock()
        ExternalResourceUploader uploader = Mock()
        ExternalResourceLister lister = Mock()
        def repository = new DefaultExternalResourceRepository('test', accessor, uploader, lister)

        when:
        repository.put(testDir.createFile(new File('test.txt')), destination)

        then:
        1 * uploader.upload(_, _, _)

        where:
        destination << [
                new URI("/some/file.sha1"),
                new URI("/some/file.md5"),
                new URI("/some/file.SHA1"),
                new URI("/some/file.MD5")
        ]
    }
}
