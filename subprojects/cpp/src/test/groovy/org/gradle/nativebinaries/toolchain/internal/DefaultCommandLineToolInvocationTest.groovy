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
package org.gradle.nativebinaries.toolchain.internal

import org.gradle.api.Action
import spock.lang.Specification

public class DefaultCommandLineToolInvocationTest extends Specification {

    def "copies and dereferences all fields"() {
        def pathEntry = Mock(File)
        def workDirectory = Mock(File)
        def invocation = new DefaultCommandLineToolInvocation();
        invocation.addEnvironmentVar("var", "value");
        invocation.addPath(pathEntry)
        invocation.workDirectory = workDirectory
        invocation.args = ["one", "two"]

        when:
        def copy = invocation.copy()

        and:
        invocation.workDirectory = pathEntry
        invocation.addPath(workDirectory)
        invocation.getEnvironment().put("var", "different")
        invocation.addEnvironmentVar("var2", "value2")
        invocation.args = ["two", "three"]

        then:
        copy.environment == [var: "value"]
        copy.path == [pathEntry]
        copy.workDirectory == workDirectory
        copy.args == ["one", "two"]
    }

    def "actions are applied to args in order"() {
        when:
        def invocation = new DefaultCommandLineToolInvocation()
        invocation.addPostArgsAction({ List<String> vals ->
            vals << "first"
        } as Action<List<String>>)

        and:
        invocation.args = ["invocation1"]

        then:
        invocation.args == ["invocation1", "first"]

        when:
        def copy = invocation.copy()
        copy.addPostArgsAction({ List<String> vals ->
            vals << "second"
        } as Action<List<String>>)

        and:
        copy.args = ["copy1"]

        then:
        copy.args == ["copy1", "first", "second"]

    }
}
