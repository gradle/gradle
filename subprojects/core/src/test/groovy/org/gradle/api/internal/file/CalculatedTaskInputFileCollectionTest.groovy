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

package org.gradle.api.internal.file

import org.gradle.api.internal.file.collections.MinimalFileSet
import org.gradle.api.internal.tasks.TaskDependencyFactory
import org.gradle.api.internal.tasks.properties.LifecycleAwareValue
import spock.lang.Specification


class CalculatedTaskInputFileCollectionTest extends Specification {
    private TaskDependencyFactory taskDependencyFactory = TestFiles.taskDependencyFactory()

    def "cannot query value before task has started executing"() {
        def calculated = Stub(MinimalFileSet)
        def fileCollection = new CalculatedTaskInputFileCollection(taskDependencyFactory, ":task", calculated)

        calculated.displayName >> "<files>"

        when:
        fileCollection.files

        then:
        def e = thrown(IllegalStateException)
        e.message == 'Can only query <files> while task :task is running'
    }

    def "cannot query value after task has completed executing"() {
        def calculated = Stub(MinimalFileSet)
        def fileCollection = new CalculatedTaskInputFileCollection(taskDependencyFactory, ":task", calculated)

        calculated.displayName >> "<files>"

        given:
        fileCollection.prepareValue()
        fileCollection.files
        fileCollection.cleanupValue()

        when:
        fileCollection.files

        then:
        def e = thrown(IllegalStateException)
        e.message == 'Can only query <files> while task :task is running'
    }

    def "caches the result during task execution"() {
        def calculated = Mock(MinimalFileSet)
        def files = [new File("f1")] as Set
        def fileCollection = new CalculatedTaskInputFileCollection(taskDependencyFactory, ":task", calculated)

        when:
        fileCollection.prepareValue()

        then:
        0 * calculated._

        when:
        def r1 = fileCollection.files
        def r2 = fileCollection.files

        then:
        r1 == files
        r2 == files

        and:
        1 * calculated.files >> files
        0 * calculated._

        when:
        fileCollection.cleanupValue()

        then:
        0 * calculated._
    }

    def "notifies each of the inputs of task start and complete"() {
        def input1 = Mock(LifecycleAwareValue)
        def input2 = "other"
        def input3 = Mock(LifecycleAwareValue)
        def fileCollection = new CalculatedTaskInputFileCollection(taskDependencyFactory, ":task", Stub(MinimalFileSet), input1, input2, input3)

        when:
        fileCollection.prepareValue()

        then:
        1 * input1.prepareValue()
        1 * input3.prepareValue()

        when:
        fileCollection.cleanupValue()

        then:
        1 * input1.cleanupValue()
        1 * input3.cleanupValue()
    }

    def "notifies calculated files of task start and complete"() {
        def calculated = Mock(TestCollection)
        def fileCollection = new CalculatedTaskInputFileCollection(taskDependencyFactory, ":task", calculated)

        when:
        fileCollection.prepareValue()

        then:
        1 * calculated.prepareValue()
        0 * calculated._

        when:
        fileCollection.cleanupValue()

        then:
        1 * calculated.cleanupValue()
        0 * calculated._
    }

    interface TestCollection extends MinimalFileSet, LifecycleAwareValue {
    }
}
