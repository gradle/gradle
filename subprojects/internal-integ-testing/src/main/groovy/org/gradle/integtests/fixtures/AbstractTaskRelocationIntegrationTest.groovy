/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.integtests.fixtures

abstract class AbstractTaskRelocationIntegrationTest extends AbstractIntegrationSpec {

    def "task is relocatable"() {
        setupProjectInOriginalLocation()

        when:
        succeeds taskName
        def originalResults = extractResults()
        then:
        executedAndNotSkipped taskName

        when:
        succeeds taskName
        then:
        skipped taskName

        when:
        moveFilesAround()
        succeeds taskName
        then:
        skipped(taskName)

        when:
        removeResults()
        succeeds taskName
        def movedResults = extractResults()
        then:
        executedAndNotSkipped taskName
        assertResultsEqual originalResults, movedResults
    }

    abstract protected String getTaskName()

    abstract protected void setupProjectInOriginalLocation()

    abstract protected void moveFilesAround()

    @SuppressWarnings("GrMethodMayBeStatic")
    protected void removeResults() {
        file("build").deleteDir()
    }

    abstract protected def extractResults()

    protected void assertResultsEqual(def originalResult, def movedResult) {
        assert movedResult == originalResult
    }
}
