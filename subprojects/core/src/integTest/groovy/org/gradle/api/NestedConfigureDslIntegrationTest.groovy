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

package org.gradle.api

import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.internal.artifacts.dsl.DefaultRepositoryHandler
import org.gradle.api.internal.tasks.DefaultTaskContainer
import org.gradle.api.tasks.TaskContainer
import org.gradle.configuration.Help
import org.gradle.integtests.fixtures.AbstractIntegrationSpec

import static org.gradle.integtests.fixtures.RepoScriptBlockUtil.mavenCentralRepositoryDefinition

class NestedConfigureDslIntegrationTest extends AbstractIntegrationSpec {
    def "can configure object using configure closure"() {
        buildFile << """
tasks.help { t ->
    assert t instanceof $Help.name
    assert delegate instanceof $Help.name
    description = "this is task \$name"
}
assert tasks.help.description == "this is task help"
"""

        expect:
        succeeds()
    }

    def "reports read unknown property from configure closure"() {
        buildFile << """
tasks.help {
    println unknown
}
"""

        expect:
        fails()
        failure.assertHasCause("Could not get unknown property 'unknown' for task ':help' of type org.gradle.configuration.Help.")
    }

    def "reports set unknown property from configure closure"() {
        buildFile << """
tasks.help {
    unknown = 12
}
"""

        expect:
        fails()
        failure.assertHasCause("Could not set unknown property 'unknown' for task ':help' of type org.gradle.configuration.Help.")
    }

    def "reports invoke unknown method from configure closure"() {
        settingsFile << "rootProject.name = 'test'"
        buildFile << """
tasks.help {
    unknown(12)
}
"""

        expect:
        fails()
        failure.assertHasCause("Could not find method unknown() for arguments [12] on task ':help' of type org.gradle.configuration.Help.")
    }

    def "can read property from configure closure outer scope"() {
        buildFile """
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
        buildFile """
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

    def "can read static property from configure closure outer scope"() {
        buildFile """
class MyPlugin implements Plugin<Project> {
    static String prop = "value"

    void apply(Project p) {
        p.repositories {
            maven { println "from apply: " + prop }
        }
        configure(p)
    }

    static void configure(def p) {
        p.repositories {
            maven { println "from static method: " + prop }
        }
    }
}

apply plugin: MyPlugin
"""

        expect:
        succeeds()
        outputContains("from apply: value")
        outputContains("from static method: value")
    }

    def "can use curried closure to configure item"() {
        buildFile << """
def cl = { String description, Task task -> task.description = description }
tasks.help cl.curry("this is the description")
assert tasks.help.description == "this is the description"
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

    def "can configure named container using configure closure"() {
        buildFile << """
configurations { c ->
//    assert c instanceof ${ConfigurationContainer.name}
//    assert delegate instanceof ${ConfigurationContainer.name}
    compile.description = "some things"
}
assert configurations.compile.description == "some things"
"""

        expect:
        succeeds()
    }

    def "can configure polymorphic container using configure closure"() {
        buildFile << """
tasks.configure { t ->
    assert t instanceof ${TaskContainer.name}
    assert delegate instanceof ${TaskContainer.name}
    help.description = "some help"
}
assert tasks.help.description == "some help"
"""

        expect:
        succeeds()
    }

    def "can configure named container when script level configure method with same name exists"() {
        buildFile << """
configurations {
    repositories {
    }
}
assert configurations.names as List == ['repositories']
assert repositories.empty
"""

        expect:
        succeeds()
    }

    def "cannot reference script level configure method from named container configure closure when that closure would fail with MME if applied to a new element"() {
        buildFile << """
configurations {
    ${mavenCentralRepository()}
    someConf {
        allprojects { }
    }
}
assert configurations.names as List == ['repositories', 'someConf'] // side effect is that the configuration is actually created
assert repositories.size() == 1
"""

        expect:
        fails "help"
        errorOutput.contains("Could not find method maven() for arguments")
    }

    def "cannot reference script level configure method from async closure in named container configure closure when that closure would fail with MME if applied to a new element"() {
        buildFile << """
plugins {
    id 'distribution'
}
${mavenCentralRepository()}

configurations {
    conf.incoming.afterResolve {
        distributions {
            myDist {
                contents {}
            }
        }
    }
}

task resolve {
    dependsOn configurations.conf
    doFirst {
        configurations.conf.files() // Trigger `afterResolve`
        assert distributions*.name.contains('myDist')
    }
}

assert configurations*.name.contains('conf')
"""

        expect:
        fails "resolve"
        errorOutput.contains("Could not find method myDist() for arguments")
    }

    def "reports missing method from inside configure closure"() {
        buildFile << """
configurations {
    broken {
        noExist(12)
    }
}
"""

        expect:
        fails()
        failure.assertHasCause("Could not find method noExist() for arguments [12] on configuration ':broken' of type org.gradle.api.internal.artifacts.configurations.DefaultLegacyConfiguration.")
    }

    def "reports set unknown property from polymorphic container configure closure"() {
        buildFile << """
tasks.configure {
    unknown = 12
}
"""

        expect:
        fails()
        failure.assertHasCause("Could not set unknown property 'unknown' for task set of type $DefaultTaskContainer.name.")
    }

    def "reports invoke unknown method from polymorphic container configure closure"() {
        settingsFile << "rootProject.name = 'test'"
        buildFile << """
tasks.configure {
    unknown (12)
}
"""

        expect:
        fails()
        failure.assertHasCause("Could not find method unknown() for arguments [12] on task set of type ${DefaultTaskContainer.name}.")
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
    assert delegate instanceof ${RepositoryHandler.name}
    ${mavenCentralRepositoryDefinition()}
}
assert repositories.size() == 1
"""

        expect:
        succeeds()
    }

    def "reports read unknown property from container configure closure"() {
        buildFile << """
repositories {
    println unknown
}
"""

        expect:
        fails()
        failure.assertHasCause("Could not get unknown property 'unknown' for repository container of type $DefaultRepositoryHandler.name.")
    }

    def "reports set unknown property from container configure closure"() {
        buildFile << """
repositories {
    unknown = 12
}
"""

        expect:
        fails()
        failure.assertHasCause("Could not set unknown property 'unknown' for repository container of type $DefaultRepositoryHandler.name.")
    }

    def "reports invoke unknown method from container configure closure"() {
        settingsFile << "rootProject.name = 'test'"
        buildFile << """
repositories {
    unknown(12)
}
"""

        expect:
        fails()
        failure.assertHasCause("Could not find method unknown() for arguments [12] on repository container of type $DefaultRepositoryHandler.name.")
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
