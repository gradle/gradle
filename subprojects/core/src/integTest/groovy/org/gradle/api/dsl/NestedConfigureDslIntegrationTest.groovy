/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.dsl

import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.tasks.TaskContainer
import org.gradle.configuration.Help
import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class NestedConfigureDslIntegrationTest extends AbstractIntegrationSpec {
    def "can configure object using configure closure"() {
        buildFile << """
tasks.help { t ->
    assert t instanceof $Help.name
    description = "this is task \$name"
}
assert tasks.help.description == "this is task help"
"""

        expect:
        succeeds()
    }

    def "can read property from configure closure outer scope"() {
        buildFile << """
ext.prop = "value"
tasks.help {
    println "1: " + prop
    1.times {
        println "2: " + prop
    }
}
"""

        expect:
        succeeds()
        outputContains("1: value")
        outputContains("2: value")
    }

    def "can set property in configure closure outer scope"() {
        buildFile << """
ext.prop = "value 1"
tasks.help {
    assert prop == "value 1"
    prop = "value 2"
    1.times {
        assert prop == "value 2"
        prop = "value 3"
    }
}
assert prop == "value 3"
"""

        expect:
        succeeds()
    }

    def "can invoke method from configure closure outer scope"() {
        buildFile << """
ext.m = { p -> "[\$p]" }
tasks.help {
    println "1: " + m(1)
    1.times {
        println "2: " + m(2)
    }
}
"""

        expect:
        succeeds()
        outputContains("1: [1]")
        outputContains("2: [2]")
    }

    def "can configure polymorphic container using configure closure"() {
        buildFile << """
tasks.configure { t ->
//    assert t instanceof ${TaskContainer.name}
    help.description = "some help"
}
assert tasks.help.description == "some help"
"""

        expect:
        succeeds()
    }

    def "can read property from polymorphic container configure closure outer scope"() {
        buildFile << """
ext.prop = "value"
tasks.configure {
    println "1: " + prop
    help {
        println "2: " + prop
        1.times {
            println "3: " + prop
        }
    }
}
"""

        expect:
        succeeds()
        outputContains("1: value")
        outputContains("2: value")
        outputContains("3: value")
    }

    def "can set property in polymorphic container configure closure outer scope"() {
        buildFile << """
ext.prop = "value 1"
tasks.configure {
    assert prop == "value 1"
    prop = "value 2"
    help {
        assert prop == "value 2"
        prop = "value 3"
        1.times {
            assert prop == "value 3"
            prop = "value 4"
        }
    }
}
assert prop == "value 4"
"""

        expect:
        succeeds()
    }

    def "can invoke method from polymorphic container configure closure outer scope"() {
        buildFile << """
ext.m = { p -> "[\$p]" }
tasks.configure {
    println "1: " + m(1)
    help {
        println "2: " + m(2)
        1.times {
            println "3: " + m(3)
        }
    }
}
"""

        expect:
        succeeds()
        outputContains("1: [1]")
        outputContains("2: [2]")
        outputContains("3: [3]")
    }

    def "can configure container in configure closure"() {
        buildFile << """
repositories { r ->
    assert r instanceof ${RepositoryHandler.name}
    mavenCentral()
}
assert repositories.size() == 1
"""

        expect:
        succeeds()
    }

    def "can read property from container configure closure outer scope"() {
        buildFile << """
ext.prop = "value"
repositories {
    println "1: " + prop
    maven {
        println "2: " + prop
        1.times {
            println "3: " + prop
            authentication {
                println "4: " + prop
            }
        }
    }
}
"""

        expect:
        succeeds()
        outputContains("1: value")
        outputContains("2: value")
        outputContains("3: value")
        outputContains("4: value")
    }

    def "can set property in container configure closure outer scope"() {
        buildFile << """
ext.prop = "value 1"
repositories {
    assert prop == "value 1"
    prop = "value 2"
    maven {
        assert prop == "value 2"
        prop = "value 3"
        1.times {
            assert prop == "value 3"
            prop = "value 4"
            authentication {
                assert prop == "value 4"
                prop = "value 5"
            }
        }
    }
}
assert prop == "value 5"
"""

        expect:
        succeeds()
    }

    def "can invoke method from container configure closure outer scope"() {
        buildFile << """
ext.m = { p -> "[\$p]" }
repositories {
    println "1: " + m(1)
    maven {
        println "2: " + m(2)
        1.times {
            println "3: " + m(3)
            authentication {
                println "4: " + m(4)
            }
        }
    }
}
"""

        expect:
        succeeds()
        outputContains("1: [1]")
        outputContains("2: [2]")
        outputContains("3: [3]")
        outputContains("4: [4]")
    }

}
