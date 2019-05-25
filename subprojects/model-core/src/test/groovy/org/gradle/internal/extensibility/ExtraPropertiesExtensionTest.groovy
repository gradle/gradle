/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.extensibility

import org.gradle.api.Project
import org.gradle.api.plugins.ExtraPropertiesExtension
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

/**
 * Contract test for dynamic extension implementations.
 *
 * @param <T> The concrete implementation type
 */
abstract class ExtraPropertiesExtensionTest<T extends ExtraPropertiesExtension> extends Specification {
    
    T extension

    def setup() {
        extension = createExtension()
    }

    abstract T createExtension()

    def "can register properties"() {
        when:
        extension.set("foo", "baz")

        then:
        extension.get("foo") == "baz"
    }

    def "cannot get or set properties that have not been added"() {
        when:
        extension.get("foo")

        then:
        thrown(ExtraPropertiesExtension.UnknownPropertyException)
    }

    def "can read/write properties using groovy notation"() {
        given:
        extension.foo = null

        expect:
        extension.foo == null

        when:
        extension.foo = "bar"

        then:
        extension.foo == "bar"
    }

    def "cannot read or write unregistered property using groovy syntax"() {
        when:
        extension.foo

        then:
        thrown(MissingPropertyException)

        when:
        extension.foo = "bar"

        then:
        extension.foo == "bar"
    }

    def "can call closure properties like methods"() {
        given:
        extension.m0 = { -> "m0" }
        extension.m1 = { it }
        extension.m2 = { String a1, String a2 -> "$a1 $a2" }
        
        expect:
        extension.m0() == "m0"
        extension.m1("foo") == "foo"
        extension.m2("foo", "bar") == "foo bar"

        when:
        extension.m0(1)

        then:
        thrown(MissingMethodException)

        and:
        extension.m1() == null

        when:
        extension.m1(1, 2)

        then:
        thrown(MissingMethodException)

        when:
        extension.m2("a")

        then:
        thrown(MissingMethodException)
        
        when:
        extension.m2(1, "a")
        
        then:
        thrown(MissingMethodException)
    }
    
    def "can get properties as a detached map"() {
        given:
        extension.p1 = 1
        extension.p2 = 2
        extension.p3 = 3
        
        and:
        def props = extension.properties.sort()
        
        expect:
        props == [p1: 1, p2: 2, p3: 3]
        
        when:
        props.p1 = 10
        
        then:
        extension.p1 == old(extension.p1)
    }
    
    def "can detect if has a property"() {
        given:
        extension.foo = "bar"
        
        expect:
        extension.has("foo")
        
        and:
        !extension.has("other")
        
        when:
        extension.foo = null
        
        then:
        extension.has("foo")
    }
    
    def "can resolve from owning context when in extension closure"() {
        given:
        Project project = ProjectBuilder.builder().build()
                
        project.configure(project) {
            extensions.add("dynamic", extension)
            version = "1.0"            
            dynamic {
                version = version // should resolve to project.version
            }    
        }
        
        expect:
        project.dynamic.version == project.version
    }

    def "can resolve method from owning context when in extension closure"() {
        given:
        Project project = ProjectBuilder.builder().build()

        project.task("custom").extensions.add("dynamic", extension)

        when:
        project.custom {
            dynamic {
                doLast { // should resolve to task.doLast

                }
            }
        }

        then:
        notThrown(Exception)
    }
    
    def "can use [] notation to get and set"() {
        when:
        extension["foo"]
        
        then:
        thrown(MissingPropertyException)

        when:
        extension["foo"] = "bar"

        then:
        extension["foo"] == "bar"
    }

    def "cannot assign to properties"() {
        when:
        extension.properties = [:]
        
        then:
        thrown(MissingPropertyException)
    }
    
    def "does not have properties property"() {
        expect:
        !extension.has("properties")
    }

}
