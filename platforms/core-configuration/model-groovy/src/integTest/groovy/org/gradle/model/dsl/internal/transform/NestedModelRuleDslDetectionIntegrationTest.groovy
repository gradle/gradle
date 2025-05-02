/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.model.dsl.internal.transform

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache

@UnsupportedWithConfigurationCache(because = "software model")
class NestedModelRuleDslDetectionIntegrationTest extends AbstractIntegrationSpec {
    def "rules can contain arbitrary code that includes closures that look like nested rules"() {
        buildFile << '''
class UnmanagedThing {
    def getSomeProp() {
        return this
    }
    def conf(Closure cl) {
        cl.delegate = this
        cl.call()
    }
}

class MyPlugin extends RuleSource {
    @Model
    UnmanagedThing thing() { return new UnmanagedThing() }
}
apply plugin: MyPlugin

model {
    thing {
        conf {
            println "outer 1"
        }
        someProp.conf {
            println "outer 2"
            // not a rule
            conf { println "inner 1" }
        }
    }
    tasks {
        show(Task) { doLast { println $.thing } }
    }
}
'''

        expect:
        succeeds "show"
        output.contains("outer 1")
        output.contains("outer 2")
        output.contains("inner 1")
    }

    def "rules can contain arbitrary code that includes methods calls with input references and closure parameters"() {
        buildFile << '''
class UnmanagedThing {
    def conf(String value, Closure cl) {
        cl.delegate = this
        cl.call(value)
    }
}

class MyPlugin extends RuleSource {
    @Model
    UnmanagedThing thing() { return new UnmanagedThing() }
    @Model
    String param() { return "param" }
}
apply plugin: MyPlugin

model {
    thing {
        conf($.param) {
            println "outer 1: " + it
            // not a rule
            conf($.param) {
                println "inner 1: " + it
            }
            conf($('param')) {
                println "inner 2: " + it
            }
        }
    }
    tasks {
        show(Task) { doLast { println $.thing } }
    }
}
'''

        expect:
        succeeds "show"
        output.contains("outer 1: param")
        output.contains("inner 1: param")
        output.contains("inner 2: param")
    }
}
