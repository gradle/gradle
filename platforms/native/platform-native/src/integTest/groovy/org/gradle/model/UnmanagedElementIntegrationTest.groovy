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

package org.gradle.model

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache

@UnsupportedWithConfigurationCache(because = "software model")
class UnmanagedElementIntegrationTest extends AbstractIntegrationSpec {

    def "can view unmanaged element as ModelElement"() {
        given:
        buildFile << '''
class Thing { }

class Rules extends RuleSource {
    @Model
    Thing thing() {
        return new Thing()
    }

    @Mutate
    void tasks(ModelMap<Task> tasks, @Path("thing") ModelElement thing) {
        tasks.create("show") {
            doLast {
                println "thing: $thing"
                println "name: $thing.name"
                println "display-name: $thing.displayName"
            }
        }
    }
}

apply plugin: Rules
'''

        when:
        run("show")

        then:
        output.contains("thing: Thing 'thing'")
        output.contains("name: thing")
        output.contains("display-name: Thing 'thing'")
    }
}
