/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.api.tasks.testing

import org.gradle.api.InvalidUserDataException
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.provider.AbstractProperty
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.util.TestUtil

class TestTest extends AbstractProjectBuilderSpec {

    def 'fails if custom executable does not exist'() {
        def task = project.tasks.create("test", Test)
        task.testClassesDirs = TestFiles.fixed(new File("tmp"))
        task.binaryResultsDirectory.fileValue(new File("out"))
        def invalidExecutable = "invalidExecutable"

        when:
        task.executable = invalidExecutable
        task.javaLauncher.get()

        then:
        def e = thrown(AbstractProperty.PropertyQueryException)
        def cause = TestUtil.getRootCause(e) as InvalidUserDataException
        cause.message.contains("The configured executable does not exist")
        cause.message.contains(invalidExecutable)
    }
}
