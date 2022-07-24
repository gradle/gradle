/*
 * Copyright 2017 the original author or authors.
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


import org.gradle.internal.resource.ExternalResourceName
import org.gradle.internal.resource.transfer.ExternalResourceAccessor
import org.gradle.internal.resource.transfer.ExternalResourceLister
import org.gradle.internal.resource.transfer.ExternalResourceUploader
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class DefaultExternalResourceRepositoryTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    def resourceAccessor = Mock(ExternalResourceAccessor)
    def resourceUploader = Mock(ExternalResourceUploader)
    def resourceLister = Mock(ExternalResourceLister)
    def repository = new DefaultExternalResourceRepository("repo", resourceAccessor, resourceUploader, resourceLister)

    def "creating resource does not access the backing resource"() {
        def name = new ExternalResourceName("resource")

        when:
        def resource = repository.resource(name, true)

        then:
        resource != null
        0 * _
    }
}
