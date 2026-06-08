/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.cc.impl.inputs.undeclared

import org.gradle.internal.cc.impl.AbstractConfigurationCacheIntegrationTest
import spock.lang.Issue
import spock.lang.Unroll

@Issue("https://github.com/gradle/gradle/issues/17344")
class UndeclaredSystemPropertiesIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    static enum PropertySource {
        REAL("System.getProperties()"),
        CLONE("((Properties) System.getProperties().clone())")

        final String groovyExpression

        PropertySource(String expr) {
            this.groovyExpression = expr
        }

        @Override
        String toString() {
            return name().toLowerCase()
        }
    }

    @Unroll
    def "read of existing original key on #source source is tracked as fingerprint input"() {
        given:
        buildFile("""
            def props = ${source.groovyExpression}
            println("foo = \${props.getProperty("foo")}")
            tasks.register("printProperty") { doLast { println("done") } }
        """)
        def configurationCache = newConfigurationCacheFixture()

        when:
        configurationCacheRun("-Dfoo=v1", "printProperty")

        then:
        problems.assertResultHasProblems(result) {
            withInput("Build file 'build.gradle': system property 'foo'")
            ignoringUnexpectedInputs()
        }

        when:
        configurationCacheRun("-Dfoo=v1", "printProperty")

        then:
        configurationCache.assertStateLoaded()

        when:
        configurationCacheRun("-Dfoo=v2", "printProperty")

        then:
        configurationCache.assertStateStored()

        where:
        source << PropertySource.values()
    }

    @Unroll
    def "read of missing original key on #source source is tracked as fingerprint input with null value"() {
        given:
        buildFile("""
            def props = ${source.groovyExpression}
            println("missing = \${props.getProperty("missing.key")}")
            tasks.register("printProperty") { doLast { println("done") } }
        """)
        def configurationCache = newConfigurationCacheFixture()

        when:
        configurationCacheRun("printProperty")

        then:
        outputContains("missing = null")
        configurationCache.assertStateStored()

        when:
        configurationCacheRun("printProperty")

        then:
        configurationCache.assertStateLoaded()

        when:
        configurationCacheRun("-Dmissing.key=v", "printProperty")

        then:
        configurationCache.assertStateStored()

        where:
        source << PropertySource.values()
    }

    @Unroll
    def "unrelated external system property change does not invalidate cache when reading #source source"() {
        given:
        buildFile("""
            def props = ${source.groovyExpression}
            println("foo = \${props.getProperty("foo")}")
            tasks.register("printProperty") { doLast { println("done") } }
        """)
        def configurationCache = newConfigurationCacheFixture()

        when:
        configurationCacheRun("-Dfoo=v1", "printProperty")

        then:
        configurationCache.assertStateStored()

        when:
        configurationCacheRun("-Dfoo=v1", "-Dunrelated.key=whatever", "printProperty")

        then:
        configurationCache.assertStateLoaded()

        where:
        source << PropertySource.values()
    }

    @Unroll
    def "setProperty then getProperty on #source source (pre-existing key) — cache reused"() {
        given:
        buildFile("""
            def props = ${source.groovyExpression}
            props.setProperty("foo", "build-value")
            println("foo = \${props.getProperty("foo")}")
            tasks.register("printProperty") { doLast { println("done") } }
        """)
        def configurationCache = newConfigurationCacheFixture()

        when:
        configurationCacheRun("-Dfoo=external", "printProperty")

        then:
        configurationCache.assertStateStored()
        outputContains("foo = build-value")

        when:
        configurationCacheRun("-Dfoo=external", "printProperty")

        then:
        configurationCache.assertStateLoaded()

        where:
        source << PropertySource.values()
    }

    @Unroll
    def "remove then getProperty on #source source returns null and hits cache"() {
        given:
        buildFile("""
            def props = ${source.groovyExpression}
            props.remove("foo")
            println("foo = \${props.getProperty("foo")}")
            tasks.register("printProperty") { doLast { println("done") } }
        """)
        def configurationCache = newConfigurationCacheFixture()

        when:
        configurationCacheRun("-Dfoo=external", "printProperty")

        then:
        configurationCache.assertStateStored()
        outputContains("foo = null")

        when:
        configurationCacheRun("-Dfoo=external", "printProperty")

        then:
        configurationCache.assertStateLoaded()

        where:
        source << PropertySource.values()
    }

    def "clear then getProperty on real source returns null and hits cache"() {
        given:
        buildFile("""
            def props = System.getProperties()
            props.clear()
            println("foo = \${props.getProperty("foo")}")
            tasks.register("printProperty") { doLast { println("done") } }
        """)
        def configurationCache = newConfigurationCacheFixture()

        when:
        configurationCacheRun("-Dfoo=external", "printProperty")

        then:
        configurationCache.assertStateStored()
        outputContains("foo = null")

        when:
        configurationCacheRun("-Dfoo=external", "printProperty")

        then:
        configurationCache.assertStateLoaded()
    }

    def "clear then getProperty on clone source returns null and hits cache"() {
        given:
        buildFile("""
            def props = ((Properties) System.getProperties().clone())
            props.clear()
            println("foo = \${props.getProperty("foo")}")
            tasks.register("printProperty") { doLast { println("done") } }
        """)
        def configurationCache = newConfigurationCacheFixture()

        when:
        configurationCacheRun("-Dfoo=external", "printProperty")

        then:
        configurationCache.assertStateStored()
        outputContains("foo = null")

        when:
        configurationCacheRun("-Dfoo=external", "printProperty")

        then:
        configurationCache.assertStateLoaded()
    }

    @Issue("https://github.com/gradle/gradle/pull/37526")
    def "mutate then read same key on clone is not tracked"() {
        given:
        buildFile("""
            def clone = (Properties) System.getProperties().clone()
            clone.setProperty("foo", "mutated")
            def captured = clone.getProperty("foo")
            tasks.register("printProperty") {
                doLast { println("captured = \${captured}") }
            }
        """)
        def configurationCache = newConfigurationCacheFixture()

        when:
        configurationCacheRun("-Dfoo=v1", "printProperty")

        then:
        configurationCache.assertStateStored()
        outputContains("captured = mutated")

        when:
        configurationCacheRun("-Dfoo=v1", "printProperty")

        then:
        configurationCache.assertStateLoaded()
        outputContains("captured = mutated")
    }

    def "untouched key read on clone is still tracked after mutating another key"() {
        given:
        buildFile("""
            def clone = (Properties) System.getProperties().clone()
            clone.setProperty("touched", "mutated")
            def captured = clone.getProperty("untouched")
            tasks.register("printProperty") {
                doLast { println("untouched = \${captured}") }
            }
        """)
        def configurationCache = newConfigurationCacheFixture()

        when:
        configurationCacheRun("-Duntouched=v1", "printProperty")

        then:
        configurationCache.assertStateStored()
        outputContains("untouched = v1")

        when:
        configurationCacheRun("-Duntouched=v1", "printProperty")

        then:
        configurationCache.assertStateLoaded()

        when: 'changing the untouched key invalidates the cache'
        configurationCacheRun("-Duntouched=v2", "printProperty")

        then:
        configurationCache.assertStateStored()
        outputContains("untouched = v2")
    }

    def "writing to cloned system properties does not modify real system properties"() {
        given:
        buildFile("""
            def clone = (Properties) System.getProperties().clone()
            clone.setProperty("clone.only.prop", "clonevalue")

            tasks.register("printProperty") {
                doLast {
                    println("clone.only.prop in task = \${System.getProperty("clone.only.prop")}")
                }
            }
        """)
        def configurationCache = newConfigurationCacheFixture()

        when:
        configurationCacheRun("printProperty")

        then:
        configurationCache.assertStateStored()
        outputContains("clone.only.prop in task = null")

        when:
        configurationCacheRun("printProperty")

        then:
        configurationCache.assertStateLoaded()
        outputContains("clone.only.prop in task = null")
    }

    def "removing from cloned system properties does not remove real system property"() {
        given:
        buildFile("""
            def clone = (Properties) System.getProperties().clone()
            clone.remove("should.survive")

            tasks.register("printProperty") {
                doLast {
                    println("should.survive in task = \${System.getProperty("should.survive")}")
                }
            }
        """)
        def configurationCache = newConfigurationCacheFixture()

        when:
        configurationCacheRun("-Dshould.survive=keepme", "printProperty")

        then:
        configurationCache.assertStateStored()
        outputContains("should.survive in task = keepme")

        when:
        configurationCacheRun("-Dshould.survive=keepme", "printProperty")

        then:
        configurationCache.assertStateLoaded()
        outputContains("should.survive in task = keepme")
    }

    def "clearing cloned system properties does not clear real system properties"() {
        given:
        buildFile("""
            def clone = (Properties) System.getProperties().clone()
            clone.clear()

            tasks.register("printProperty") {
                doLast {
                    println("user.home in task = \${System.getProperty("user.home")}")
                }
            }
        """)
        def configurationCache = newConfigurationCacheFixture()

        when:
        configurationCacheRun("printProperty")

        then:
        configurationCache.assertStateStored()

        when:
        configurationCacheRun("printProperty")

        then:
        configurationCache.assertStateLoaded()
        outputDoesNotContain("user.home in task = null")
    }

    def "clone keySet/entrySet mutations do not leak to real system properties"() {
        given:
        buildFile("""
            def clone = (Properties) System.getProperties().clone()
            clone.keySet().remove("should.survive.keyset")
            clone.entrySet().removeIf { it.key == "should.survive.entryset" }

            tasks.register("printProperty") {
                doLast {
                    println("keyset.prop = \${System.getProperty("should.survive.keyset")}")
                    println("entryset.prop = \${System.getProperty("should.survive.entryset")}")
                }
            }
        """)
        def configurationCache = newConfigurationCacheFixture()

        when:
        configurationCacheRun("-Dshould.survive.keyset=a", "-Dshould.survive.entryset=b", "printProperty")

        then:
        configurationCache.assertStateStored()
        outputContains("keyset.prop = a")
        outputContains("entryset.prop = b")

        when:
        configurationCacheRun("-Dshould.survive.keyset=a", "-Dshould.survive.entryset=b", "printProperty")

        then:
        configurationCache.assertStateLoaded()
        outputContains("keyset.prop = a")
        outputContains("entryset.prop = b")
    }

    def "modifying cloned system properties does not affect original"() {
        given:
        buildFile("""
            println("Hello1 = \${System.getProperty("Hello")}")
            System.setProperty("Hello", "World")
            def copy = (Properties) System.getProperties().clone()
            copy.setProperty("Hello", "Bug")
            println("Hello2 = \${System.getProperty("Hello")}")

            tasks.register("printProperty") { doLast { println("done") } }
        """)
        def configurationCache = newConfigurationCacheFixture()

        when:
        configurationCacheRun("printProperty")

        then:
        configurationCache.assertStateStored()
        outputContains("Hello1 = null")
        outputContains("Hello2 = World")

        when:
        configurationCacheRun("printProperty")

        then:
        configurationCache.assertStateLoaded()
    }

    def "real System.setProperty is still tracked correctly when clone is also used"() {
        given:
        buildFile("""
            def clone = (Properties) System.getProperties().clone()
            println("clone.foo = \${clone.getProperty("foo")}")
            System.setProperty("foo", "set-by-build")

            tasks.register("printProperty") {
                doLast {
                    println("foo in task = \${System.getProperty("foo")}")
                }
            }
        """)
        def configurationCache = newConfigurationCacheFixture()

        when:
        configurationCacheRun("-Dfoo=external", "printProperty")

        then:
        configurationCache.assertStateStored()
        outputContains("clone.foo = external")
        outputContains("foo in task = set-by-build")

        when:
        configurationCacheRun("-Dfoo=external", "printProperty")

        then:
        configurationCache.assertStateLoaded()
        outputContains("foo in task = set-by-build")
    }
}
