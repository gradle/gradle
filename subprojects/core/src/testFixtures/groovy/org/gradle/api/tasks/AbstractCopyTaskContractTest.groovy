/*
 * Copyright 2013 the original author or authors.
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
import org.gradle.api.file.FileCopyDetails
import org.gradle.api.internal.DynamicObject
import org.junit.Test
import spock.lang.Unroll

/**
 * Tests that different types of copy tasks correctly expose DSL enhanced objects.
 */
abstract class AbstractCopyTaskContractTest extends AbstractConventionTaskTest {

    abstract AbstractCopyTask getTask()

    @Unroll
    @Test
    public void rootLevelFileCopyDetailsIsDslEnhanced() {
        task.eachFile {
            assert delegate instanceof DynamicObject
        }
        task.eachFile(new Action<FileCopyDetails>() {
            void execute(FileCopyDetails fcd) {
                assert fcd instanceof DynamicObject
            }
        })
    }
}
