/*
 * Copyright 2012 the original author or authors.
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

import org.gradle.api.tasks.TaskInputs
import spock.lang.Specification
import spock.lang.Unroll

class WarningEmittedOnConfiguringTaskInputsTest extends Specification {

    TaskStatusNagger taskStatusNagger = Mock()
    TaskInputs delegateTaskInputs = Mock()
    WarningEmittedOnConfiguringTaskInputs taskInputs = new WarningEmittedOnConfiguringTaskInputs(delegateTaskInputs, taskStatusNagger)

    @Unroll
    def "#methodName method call triggers taskStatusNagger"() {
        when:
        taskInputs."${method}"(param)
        then:
        1 * taskStatusNagger.nagIfTaskNotInConfigurableState("TaskInputs.${methodName}")
        where:
        method       | methodName          | param
        "files"      | "files(Object...)"  | ["afile", "bFile"]
        "file"       | "file(Object)"      | "afile"
        "dir"        | "dir(Object)"       | "afile"
        "sourceDir"  | "sourceDir(Object)" | "afile"
        "source"     | "source(Object)"    | "afile"
        "source"     | "source(Object...)" | ["afile", "bfile"].toArray()
        "properties" | "properties(Map)"   | [name: "value"]
    }

    def "property(name, value) method call triggers taskStatusNagger"() {
        when:
        taskInputs.property("name", "value")
        then:
        taskStatusNagger.nagIfTaskNotInConfigurableState("TaskInputs.property(Object,Object")
    }

    @Unroll
    def "delegates #method method calls to delegateTaskInputs"() {
        when:
        taskInputs."${method}"()
        then:
        1 * delegateTaskInputs."${method}"()
        where:
        method << ["getHasInputs", "getFiles", "getProperties", "getHasSourceFiles", "getSourceFiles"]
    }

    def "delegates dir method calls to delegateTaskInputs"() {
        when:
        taskInputs.dir("dirPath")
        then:
        1 * delegateTaskInputs.dir("dirPath")
    }

    def "delegates source method calls to delegateTaskInputs"() {
        when:
        taskInputs.source("source1")
        taskInputs.source("source1", "source2")
        then:
        1 * delegateTaskInputs.source("source1")
        1 * delegateTaskInputs.source("source1", "source2")
    }

    def "delegates sourceDir method calls to delegateTaskInputs"() {
        when:
        taskInputs.sourceDir("source1")
        then:
        1 * delegateTaskInputs.sourceDir("source1")
    }


    def "delegates property method calls to delegateTaskInputs"() {
        when:
        taskInputs.property("name", "value")
        then:
        1 * delegateTaskInputs.property("name", "value")
    }

    def "delegates properties method calls to delegateTaskInputs"() {
        setup:
        def map = [:]
        when:
        taskInputs.properties(map)
        then:
        1 * delegateTaskInputs.properties(map)
    }
}
