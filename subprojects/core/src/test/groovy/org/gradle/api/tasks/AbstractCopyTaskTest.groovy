/*
 * Copyright 2009 the original author or authors.
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

import org.gradle.api.internal.file.copy.CopyAction
import org.gradle.internal.Actions
import org.gradle.internal.Transformers
import org.gradle.test.fixtures.file.WorkspaceTest
import org.gradle.util.TestUtil
import spock.lang.Unroll

@SuppressWarnings("GroovyPointlessBoolean")
class AbstractCopyTaskTest extends WorkspaceTest {

    TestCopyTask task

    def setup() {
        task = TestUtil.create(temporaryFolder).task(TestCopyTask)
    }

    def "copy spec methods delegate to main spec of copy action"() {
        given:
        file("include") << "bar"

        expect:
        task.rootSpec.hasSource() == false

        when:
        task.from testDirectory.absolutePath
        task.include "include"

        then:
        task.mainSpec.getIncludes() == ["include"].toSet()
        task.mainSpec.buildRootResolver().source.files == task.project.fileTree(testDirectory).files
    }

    @Unroll
    def "task output caching is disabled when #description is used"() {
        expect:
        task.outputs.taskCaching.cacheable == false

        when:
        task.outputs.cacheIf("Enable caching") { true }
        then:
        task.outputs.taskCaching.cacheable == true

        when:
        method(task)
        then:
        task.outputs.taskCaching.cacheable == false

        where:
        description                 | method
        "outputs.cacheIf { false }" | { TestCopyTask task -> task.outputs.cacheIf("Manually disable caching") { false } }
        "eachFile(Closure)"         | { TestCopyTask task -> task.eachFile {} }
        "eachFile(Action)"          | { TestCopyTask task -> task.eachFile(Actions.doNothing()) }
        "expand(Map)"               | { TestCopyTask task -> task.expand([:]) }
        "filter(Closure)"           | { TestCopyTask task -> task.filter {} }
        "filter(Class)"             | { TestCopyTask task -> task.filter(FilterReader) }
        "filter(Map, Class)"        | { TestCopyTask task -> task.filter([:], FilterReader) }
        "filter(Transformer)"       | { TestCopyTask task -> task.filter(Transformers.noOpTransformer()) }
        "rename(Closure)"           | { TestCopyTask task -> task.rename {} }
        "rename(Pattern, String)"   | { TestCopyTask task -> task.rename(/(.*)/, '$1') }
        "rename(Transformer)"       | { TestCopyTask task -> task.rename(Transformers.noOpTransformer()) }
    }

    static class TestCopyTask extends AbstractCopyTask {
        CopyAction copyAction

        protected CopyAction createCopyAction() {
            copyAction
        }

        @OutputDirectory
        File getDestinationDir() {
            project.file("dest")
        }
    }
}
