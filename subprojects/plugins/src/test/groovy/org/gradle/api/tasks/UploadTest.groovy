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

import org.gradle.api.Action
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification

class UploadTest extends Specification {

    @Rule
    public TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    def "can create task"() {
        when:
        TestUtil.create(temporaryFolder).task(Upload)

        then:
        noExceptionThrown()
    }

    def "can configure repositories with an Action"() {
        given:
        def upload = TestUtil.create(temporaryFolder).task(Upload)

        expect:
        upload.repositories.size() == 0

        when:
        upload.repositories({ RepositoryHandler repositories ->
            repositories.jcenter()
        } as Action<RepositoryHandler>)

        then:
        upload.repositories.size() == 1
    }

}
