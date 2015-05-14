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

package org.gradle.api.reporting.model

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class ModelReportIntegrationTest extends AbstractIntegrationSpec {
    def "displays basic structure of an empty project"() {
        when:
        run "model"

        then:
        // just check that it doesn't blow up for now
        output.contains("tasks")
    }

    def "displays basic structure of a polyglot project"() {
        given:
        buildFile << """
plugins {
    id 'jvm-component'
    id 'java-lang'
    id 'cpp'
    id 'c'
}

model {
    components {
        jvmLib(JvmLibrarySpec)
        nativeLib(NativeLibrarySpec)
    }
}
"""

        when:
        run "model"

        then:
        // just check that it doesn't blow up for now
        output.contains("components")
        output.contains("tasks")
    }

    def "displays basic of a simple model graph with values"() {
        given:
        buildFile << """

@Managed
public interface PasswordCredentials {
    String getUsername()
    String getPassword()
    void setUsername(String s)
    void setPassword(String s)
}


@Managed
public interface Numbers {
    Integer getValue()
    void setValue(Integer i)
}

model {
    primaryCredentials(PasswordCredentials){
        username = 'uname'
        password = 'hunter2'
    }

    numbers(Numbers){
        value = 5
    }
}
"""

        when:
        run "model"

        then:
        output.contains("value = 5")
        output.contains("password = hunter2")
        output.contains("username = uname")
    }
}
