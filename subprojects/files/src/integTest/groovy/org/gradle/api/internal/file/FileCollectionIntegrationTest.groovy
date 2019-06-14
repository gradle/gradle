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

package org.gradle.api.internal.file

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class FileCollectionIntegrationTest extends AbstractIntegrationSpec {
    def "can finalize file collection using API"() {
        buildFile << """
            def files = objects.fileCollection()
            def name = 'a'
            files.from({ name })

            files.finalizeValue()
            name = 'b'
            
            assert files.files as List == [file('a')]
            
            files.from('b')
        """

        when:
        fails()

        then:
        failure.assertHasCause("The value for file collection is final and cannot be changed.")
    }
}
