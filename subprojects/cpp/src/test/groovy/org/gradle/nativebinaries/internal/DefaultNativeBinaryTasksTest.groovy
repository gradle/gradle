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

package org.gradle.nativebinaries.internal

import org.gradle.nativebinaries.tasks.CreateStaticLibrary
import org.gradle.nativebinaries.tasks.LinkExecutable
import org.gradle.util.TestUtil
import spock.lang.Specification;

class DefaultNativeBinaryTasksTest extends Specification {
    def tasks = new DefaultNativeBinaryTasks()

    def "returns null for link, createStaticLib and builder when none defined"() {
        expect:
        tasks.link == null
        tasks.createStaticLib == null
        tasks.builder == null
    }

    def "returns link task when defined"() {
        when:
        final linkTask = TestUtil.createTask(LinkExecutable)
        tasks.add(linkTask)

        then:
        tasks.link == linkTask
        tasks.createStaticLib == null
        tasks.builder == linkTask
    }

    def "returns create task when defined"() {
        when:
        final createTask = TestUtil.createTask(CreateStaticLibrary)
        tasks.add(createTask)

        then:
        tasks.link == null
        tasks.createStaticLib == createTask
        tasks.builder == createTask
    }
}
