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

package org.gradle.internal.execution.steps

import org.gradle.api.file.FileCollection
import org.gradle.internal.execution.Step
import org.gradle.internal.execution.UnitOfWork
import org.gradle.internal.file.TreeType
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class StepSpec extends Specification {
    @Rule
    final TestNameTestDirectoryProvider temporaryFolder = TestNameTestDirectoryProvider.newInstance()

    final delegate = Mock(Step)

    final work = Mock(UnitOfWork)

    protected TestFile file(Object... path) {
        return temporaryFolder.file(path)
    }

    def outputFileProperty(String name, TreeType type, FileCollection roots) {
        return new UnitOfWork.OutputFileProperty() {
            @Override
            String getName() { name }

            @Override
            TreeType getType() { type }

            @Override
            FileCollection getRoots() { roots }
        }
    }
}
