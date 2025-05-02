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

package org.gradle.api

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache

class BuildScriptVisibilityIntegrationTest extends AbstractIntegrationSpec {
    @ToBeFixedForConfigurationCache(because = "test expects scripts evaluation")
    def "methods defined in project build script are visible to descendant projects"() {
        createDirs("child1")
        settingsFile << "include 'child1'"
        buildFile """
def doSomething(def value) {
    return "{" + value + "}"
}
private String doSomethingElse(def value) {
    return "[" + value + "]"
}
println "root: " + doSomething(10)
println "root: " + doSomethingElse(10)
"""
        file("child1/build.gradle") << """
println "child: " + doSomething(11)
println "child: " + doSomethingElse(11)
"""

        expect:
        // Invoke twice to exercise script caching
        succeeds()
        outputContains("root: {10}")
        outputContains("root: [10]")
        outputContains("child: {11}")
        outputContains("child: [11]")

        and:
        succeeds()
        outputContains("root: {10}")
        outputContains("root: [10]")
        outputContains("child: {11}")
        outputContains("child: [11]")
    }

    @ToBeFixedForConfigurationCache(because = "test expects scripts evaluation")
    def "methods defined in project build script are visible to script plugins applied to project and descendants"() {
        createDirs("child1")
        settingsFile << "include 'child1'"
        buildFile << """
def doSomething(def value) {
    return "{" + value + "}"
}
private String doSomethingElse(def value) {
    return "[" + value + "]"
}
apply from: 'script.gradle'
"""
        file("child1/build.gradle") << """
apply from: '../script.gradle'
"""
        file("script.gradle") << """
println project.path + " - " + doSomething(12)
println project.path + " - " + doSomethingElse(12)
"""

        expect:
        // Invoke twice to exercise script caching
        succeeds()
        outputContains(": - {12}")
        outputContains(": - [12]")
        outputContains(":child1 - {12}")
        outputContains(":child1 - [12]")

        and:
        succeeds()
        outputContains(": - {12}")
        outputContains(": - [12]")
        outputContains(":child1 - {12}")
        outputContains(":child1 - [12]")
    }

    @ToBeFixedForConfigurationCache(because = "test expects scripts evaluation")
    def "methods defined in project build script are visible to descendant projects when script contains only methods"() {
        createDirs("child1")
        settingsFile << "include 'child1'"
        buildFile << """
def doSomething(def value) {
    return value.toString()
}
"""
        file("child1/build.gradle") << """
println "child: " + doSomething(11)
"""

        expect:
        // Invoke twice to exercise script caching
        succeeds()
        outputContains("child: 11")

        and:
        succeeds()
        outputContains("child: 11")
    }

    @ToBeFixedForConfigurationCache(because = "test expects scripts evaluation")
    def "methods defined in project build script are visible to descendant projects when script contains only methods and model block"() {
        createDirs("child1")
        settingsFile << "include 'child1'"
        buildFile << """
def doSomething(def value) {
    return value.toString()
}

model {
    tasks {
        hello(Task)
    }
}
"""
        file("child1/build.gradle") << """
println "child: " + doSomething(11)
"""

        expect:
        // Invoke twice to exercise script caching
        succeeds("hello")
        outputContains("child: 11")

        and:
        succeeds("hello")
        outputContains("child: 11")
    }

    @ToBeFixedForConfigurationCache(because = "test expects scripts evaluation")
    def "properties defined in project build script are not visible to descendant projects"() {
        createDirs("child1")
        settingsFile << "include 'child1'"
        buildFile << """
def getProp1() {
    return "abc"
}

@groovy.transform.Field
String prop2

@groovy.transform.Field
String prop3 = "abc"

int prop4 = 12

prop2 = prop1

assert prop1 == "abc"
assert prop2 == "abc"
assert prop3 == "abc"
assert prop4 == 12
"""
        file("child1/build.gradle") << """
try {
    prop1
    assert false
} catch(MissingPropertyException e) {
    assert e.property == 'prop1'
}
try {
    prop2
    assert false
} catch(MissingPropertyException e) {
    assert e.property == 'prop2'
}
try {
    prop3
    assert false
} catch(MissingPropertyException e) {
    assert e.property == 'prop3'
}
try {
    prop4
    assert false
} catch(MissingPropertyException e) {
    assert e.property == 'prop4'
}
println "child1 ok"
"""

        expect:
        // Invoke twice to exercise script caching
        succeeds()
        outputContains("child1 ok")

        and:
        succeeds()
        outputContains("child1 ok")
    }

    @ToBeFixedForConfigurationCache(because = "test expects scripts evaluation")
    def "properties defined in project build script are not visible to script plugins"() {
        createDirs("child1")
        settingsFile << "include 'child1'"
        buildFile << """
def getProp1() {
    return "abc"
}

@groovy.transform.Field
String prop2

prop2 = prop1

assert prop1 == "abc"
assert prop2 == "abc"
apply from: 'script.gradle'
"""
        file("child1/build.gradle") << """
apply from: '../script.gradle'
"""

        file("script.gradle") << """
try {
    prop1
    assert false
} catch(MissingPropertyException e) {
    assert e.property == 'prop1'
}
try {
    prop2
    assert false
} catch(MissingPropertyException e) {
    assert e.property == 'prop2'
}
println project.path + " ok"
"""

        expect:
        // Invoke twice to exercise script caching
        succeeds()
        outputContains(": ok")
        outputContains(":child1 ok")

        and:
        succeeds()
        outputContains(": ok")
        outputContains(":child1 ok")
    }
}
