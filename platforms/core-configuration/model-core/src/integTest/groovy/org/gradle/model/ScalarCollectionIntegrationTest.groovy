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
class ScalarCollectionIntegrationTest extends AbstractIntegrationSpec {
    def "can create instance of #{type.name}"() {
        given:
        buildFile << """
class Rules extends RuleSource {
    @Model
    void strings(${type.name}<String> s) {
    }

    @Mutate
    void tasks(ModelMap<Task> tasks, @Path("strings") def strings) {
        tasks.create("show") {
            doLast {
                println "strings: \$strings"
            }
        }
    }
}

apply plugin: Rules
"""

        when:
        run("show")

        then:
        output.contains("strings: []")

        where:
        type << [Set, List]
    }

    def "can view #{type.name} as ModelElement"() {
        given:
        buildFile << """
class Rules extends RuleSource {
    @Model
    void strings(${type.name}<String> s) {
    }

    @Mutate
    void tasks(ModelMap<Task> tasks, @Path("strings") ModelElement strings) {
        tasks.create("show") {
            doLast {
                println "strings: \$strings"
                println "name: \$strings.name"
                println "display-name: \$strings.displayName"
            }
        }
    }
}

apply plugin: Rules
"""

        when:
        run("show")

        then:
        output.contains("strings: ${type.simpleName}<String> 'strings'")
        output.contains("name: strings")
        output.contains("display-name: ${type.simpleName}<String> 'strings'")

        where:
        type << [Set, List]
    }
}
