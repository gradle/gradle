/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.internal.tasks

import org.junit.Test
import static org.junit.Assert.*
import static org.hamcrest.Matchers.*
import static org.gradle.util.Matchers.*
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.TaskInternal;

class DefaultTaskOutputsTest {
    private final DefaultTaskOutputs outputs = new DefaultTaskOutputs({new File(it)} as FileResolver)

    @Test
    public void defaultValues() {
        assertThat(outputs.files.files, isEmpty())
        assertFalse(outputs.hasOutputFiles)
    }

    @Test
    public void canRegisterOutputFiles() {
        outputs.files('a')
        assertThat(outputs.files.files, equalTo([new File('a')] as Set))
    }

    @Test
    public void hasInputFilesWhenEmptyInputFilesRegistered() {
        outputs.files([])
        assertTrue(outputs.hasOutputFiles)
    }
    
    @Test
    public void hasInputFilesWhenNonEmptyInputFilesRegistered() {
        outputs.files('a')
        assertTrue(outputs.hasOutputFiles)
    }
    
    @Test
    public void canSpecifyUpToDatePredicateUsingClosure() {
        boolean upToDate = false
        outputs.upToDateWhen { upToDate }

        assertFalse(outputs.upToDateSpec.isSatisfiedBy([:] as TaskInternal))

        upToDate = true

        assertTrue(outputs.upToDateSpec.isSatisfiedBy([:] as TaskInternal))
    }
}