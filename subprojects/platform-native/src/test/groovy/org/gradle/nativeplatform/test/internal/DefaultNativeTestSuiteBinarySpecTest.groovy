/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.nativeplatform.test.internal

import org.gradle.nativeplatform.tasks.LinkExecutable
import org.gradle.nativeplatform.test.tasks.RunTestExecutable
import org.gradle.util.TestUtil
import spock.lang.Specification

class DefaultNativeTestSuiteBinarySpecTest extends Specification {
    def tasks = new DefaultNativeTestSuiteBinarySpec.DefaultNativeBinaryTasks()

    def "returns null for link, builder and run when none defined"() {
        expect:
        tasks.link == null
        tasks.createOrLink == null
        tasks.run == null
    }

    def "returns link task when defined"() {
        when:
        final linkTask = TestUtil.createTask(LinkExecutable)
        tasks.add(linkTask)

        then:
        tasks.link == linkTask
        tasks.createOrLink == linkTask
        tasks.run == null
    }

    def "returns run task when defined"() {
        when:
        final runTask = TestUtil.createTask(RunTestExecutable)
        tasks.add(runTask)

        then:
        tasks.link == null
        tasks.createOrLink == null
        tasks.run == runTask
    }
}
