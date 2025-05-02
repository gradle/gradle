/*
 * Copyright 2021 the original author or authors.
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

import groovy.test.NotYetImplemented
import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class LazyAdditionToDomainObjectCollectionIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        buildFile << """
            abstract class Base {}
            abstract class Sub extends Base {}
            
            def container = objects.domainObjectSet(Base) 
            
            def create(type) {
                def ret = objects.newInstance(type)
                println "created " + ret
                return ret
            }
        """
    }

    def "addLater(Base) triggers configuration with eager all"() {
        buildFile << """
            container.all {
                println "all called on " + it
            }
            container.addLater(provider { create(Base) })
        """
        expect:
        succeeds("help")
        outputContains("all called on ")
    }
    def "addLater(Base) triggers configuration with eager withType(Base)"() {
        buildFile << """
            container.withType(Base) {
                println "withType(Base) called on " + it
            }
            container.addLater(provider { create(Base) })
        """
        expect:
        succeeds("help")
        outputContains("withType(Base) called on ")
    }
    def "addLater(Base) triggers configuration with eager withType(Sub)"() {
        buildFile << """
            container.withType(Sub) {
                println "withType(Sub) called on " + it
            }
            container.addLater(provider { create(Base) })
        """
        expect:
        succeeds("help")
        outputDoesNotContain("withType(Sub) called on")
    }

    def "addLater(Sub) triggers configuration with eager all"() {
        buildFile << """
            container.all {
                println "all called on " + it
            }
            container.addLater(provider { create(Sub) })
        """
        expect:
        succeeds("help")
        outputContains("all called on ")
    }
    def "addLater(Sub) triggers configuration with eager withType(Base)"() {
        buildFile << """
            container.withType(Base) {
                println "withType(Base) called on " + it
            }
            container.addLater(provider { create(Sub) })
        """
        expect:
        succeeds("help")
        outputContains("withType(Base) called on ")
    }
    def "addLater(Sub) triggers configuration with eager withType(Sub)"() {
        buildFile << """
            container.withType(Sub) {
                println "withType(Sub) called on " + it
            }
            container.addLater(provider { create(Sub) })
        """
        expect:
        succeeds("help")
        outputContains("withType(Sub) called on")
    }

    def "addAllLater(Base) triggers configuration with eager all"() {
        buildFile << """
            container.all {
                println "all called on " + it
            }
            def list = objects.listProperty(Base)
            list.addAll(create(Base))
            container.addAllLater(list)
        """
        expect:
        succeeds("help")
        outputContains("all called on ")
    }
    def "addAllLater(Base) triggers configuration with eager withType(Base)"() {
        buildFile << """
            container.withType(Base) {
                println "withType(Base) called on " + it
            }
            def list = objects.listProperty(Base)
            list.addAll(create(Base))
            container.addAllLater(list)
        """
        expect:
        succeeds("help")
        outputContains("withType(Base) called on ")
    }
    def "addAllLater(Base) triggers configuration with eager withType(Sub)"() {
        buildFile << """
            container.withType(Sub) {
                println "withType(Sub) called on " + it
            }
            def list = objects.listProperty(Base)
            list.addAll(create(Base))
            container.addAllLater(list)
        """
        expect:
        succeeds("help")
        outputDoesNotContain("withType(Sub) called on")
    }

    def "addAllLater(Sub) triggers configuration with eager all"() {
        buildFile << """
            container.all {
                println "all called on " + it
            }
            def list = objects.listProperty(Sub)
            list.addAll(create(Sub))
            container.addAllLater(list)
        """
        expect:
        succeeds("help")
        outputContains("all called on ")
    }
    def "addAllLater(Sub) triggers configuration with eager withType(Base)"() {
        buildFile << """
            container.withType(Base) {
                println "withType(Base) called on " + it
            }
            def list = objects.listProperty(Sub)
            list.addAll(create(Sub))
            container.addAllLater(list)
        """
        expect:
        succeeds("help")
        outputContains("withType(Base) called on ")
    }
    def "addAllLater(Sub) triggers configuration with eager withType(Sub)"() {
        buildFile << """
            container.withType(Sub) {
                println "withType(Sub) called on " + it
            }
            def list = objects.listProperty(Sub)
            list.addAll(create(Sub))
            container.addAllLater(list)
        """
        expect:
        succeeds("help")
        outputContains("withType(Sub) called on")
    }

    def "addAllLater(Base, Sub) triggers configuration with eager all"() {
        buildFile << """
            container.all {
                println "all called on " + it
            }
            def list = objects.listProperty(Base)
            list.addAll(create(Base), create(Sub))
            container.addAllLater(list)
        """
        expect:
        succeeds("help")
        outputContains("all called on ")
    }
    def "addAllLater(Base, Sub) triggers configuration with eager withType(Base)"() {
        buildFile << """
            container.withType(Base) {
                println "withType(Base) called on " + it
            }
            def list = objects.listProperty(Base)
            list.addAll(create(Base), create(Sub))
            container.addAllLater(list)
        """
        expect:
        succeeds("help")
        outputContains("withType(Base) called on ")
    }

    @NotYetImplemented
    def "addAllLater(Base, Sub) triggers configuration with eager withType(Sub)"() {
        buildFile << """
            container.withType(Sub) {
                println "withType(Sub) called on " + it
            }
            def list = objects.listProperty(Base)
            list.addAll(create(Base), create(Sub))
            container.addAllLater(list)
        """
        expect:
        succeeds("help")
        outputContains("withType(Sub) called on")
    }

    def "addAllLater(Base, Sub) accepts non-collection provider types with eager all"() {
        buildFile << """
            container.all {
                println "all called on " + it
            }
            container.addAllLater(provider { [create(Base), create(Sub)] })
        """
        expect:
        succeeds("help")
        outputContains("all called on")
    }

    def "addAllLater(Base, Sub) accepts non-collection provider types with eager withType(Base)"() {
        buildFile << """
            container.withType(Base) {
                println "withType(Base) called on " + it
            }
            container.addAllLater(provider { [create(Base), create(Sub)] })
        """
        expect:
        succeeds("help")
        outputContains("withType(Base) called on")
    }
}
