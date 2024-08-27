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

package org.gradle.api.internal.tasks.testing.junit

import org.gradle.api.Action
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.junit.JUnitOptions
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.util.TestUtil
import spock.lang.Specification

class JUnitTestFrameworkTest extends Specification {
    private project = ProjectBuilder.builder().build()
    Test testTask = TestUtil.createTask(Test, project)

    def "can configure JUnit with an Action"() {
        when:
        testTask.useJUnit({ JUnitOptions options ->
            options.includeCategories = ['ExcludedCategory'] as Set
        } as Action<JUnitOptions>)

        then:
        (testTask.options as JUnitOptions).includeCategories.get() == ['ExcludedCategory'] as Set
    }
}
