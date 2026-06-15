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

@Issue("https://github.com/gradle/gradle/issues/17344")
class UndeclaredSystemPropertiesIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def configurationCache = newConfigurationCacheFixture()

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

    def "read of existing original key on #source source is tracked as fingerprint input"() {
        given:
        buildFile """
            def props = ${source.groovyExpression}
            def captured = props.getProperty("foo")
            tasks.register("printProperty") {
                doLast { println("foo = \${captured}") }
            }
        """

        when:
        configurationCacheRun("-Dfoo=v1", "printProperty")

        then:
        problems.assertResultHasProblems(result) {
            withInput("Build file 'build.gradle': system property 'foo'")
            ignoringUnexpectedInputs()
        }
        outputContains("foo = v1")

        when:
        configurationCacheRun("-Dfoo=v1", "printProperty")

        then:
        configurationCache.assertStateLoaded()
        outputContains("foo = v1")

        when:
        configurationCacheRun("-Dfoo=v2", "printProperty")

        then:
        configurationCache.assertStateStored()
        outputContains("foo = v2")

        where:
        source << PropertySource.values()
    }

    def "read of missing original key on #source source is tracked as fingerprint input with null value"() {
        given:
        buildFile """
            def props = ${source.groovyExpression}
            def captured = props.getProperty("missing.key")
            tasks.register("printProperty") {
                doLast { println("missing = \${captured}") }
            }
        """

        when:
        configurationCacheRun("printProperty")

        then:
        outputContains("missing = null")
        configurationCache.assertStateStored()

        when:
        configurationCacheRun("printProperty")

        then:
        configurationCache.assertStateLoaded()
        outputContains("missing = null")

        when:
        configurationCacheRun("-Dmissing.key=v", "printProperty")

        then:
        configurationCache.assertStateStored()
        outputContains("missing = v")

        where:
        source << PropertySource.values()
    }

    def "unrelated external system property change does not invalidate cache when reading #source source"() {
        given:
        buildFile """
            def props = ${source.groovyExpression}
            def captured = props.getProperty("foo")
            tasks.register("printProperty") {
                doLast { println("foo = \${captured}") }
            }
        """

        when:
        configurationCacheRun("-Dfoo=v1", "printProperty")

        then:
        configurationCache.assertStateStored()
        outputContains("foo = v1")

        when:
        configurationCacheRun("-Dfoo=v1", "-Dunrelated.key=whatever", "printProperty")

        then:
        configurationCache.assertStateLoaded()
        outputContains("foo = v1")

        where:
        source << PropertySource.values()
    }

    def "setProperty then getProperty on #source source (pre-existing key) — cache reused"() {
        given:
        buildFile """
            def props = ${source.groovyExpression}
            props.setProperty("foo", "build-value")
            def captured = props.getProperty("foo")
            tasks.register("printProperty") {
                doLast { println("foo = \${captured}") }
            }
        """

        when:
        configurationCacheRun("-Dfoo=external", "printProperty")

        then:
        configurationCache.assertStateStored()
        outputContains("foo = build-value")

        when:
        configurationCacheRun("-Dfoo=external", "printProperty")

        then:
        configurationCache.assertStateLoaded()
        outputContains("foo = build-value")

        where:
        source << PropertySource.values()
    }

    def "#mutation then getProperty on #source source returns null and hits cache"() {
        given:
        buildFile """
            def props = ${source.groovyExpression}
            props.${mutation}
            def captured = props.getProperty("foo")
            tasks.register("printProperty") {
                doLast { println("foo = \${captured}") }
            }
        """

        when:
        configurationCacheRun("-Dfoo=external", "printProperty")

        then:
        configurationCache.assertStateStored()
        outputContains("foo = null")

        when:
        configurationCacheRun("-Dfoo=external", "printProperty")

        then:
        configurationCache.assertStateLoaded()
        outputContains("foo = null")

        where:
        // Clearing the REAL System.getProperties() is deliberately excluded.
        // Config-time system-property mutations are replayed onto the live System.getProperties()
        // while the fingerprint is verified on a cache hit and, unlike on a cache miss, they are NOT
        // rolled back afterwards (see ConfigurationCacheFingerprintChecker and
        // DefaultConfigurationCache.checkFingerprint). Replaying a full clear() therefore wipes
        // JVM-essential properties (java.specification.version, file.encoding, ...) and breaks the
        // runtime mid-load — e.g. Groovy's VMPluginFactory fails to initialize — which makes this case
        // fail under --no-daemon with parallel configuration cache (seen on Linux JDK 17 and Windows
        // JDK 25). Surgical removal of a single real key is safe to replay
        // (covered by remove("foo")), and clearing a clone never touches the real properties (covered
        // by the dedicated clone tests below). Tests that genuinely clear the real properties restore
        // them immediately and run on the daemon executor only — see UndeclaredBuildInputsIntegrationTest.
        [source, mutation] << [(PropertySource.values() as List), ['remove("foo")', 'clear()']]
            .combinations()
            .findAll { src, mut -> !(src == PropertySource.REAL && mut == 'clear()') }
    }

    @Issue("https://github.com/gradle/gradle/pull/37526")
    def "putAll then read same key on clone is not tracked"() {
        given:
        buildFile """
            def clone = (Properties) System.getProperties().clone()
            clone.putAll([foo: "mutated"])
            def captured = clone.getProperty("foo")
            tasks.register("printProperty") {
                doLast { println("captured = \${captured}") }
            }
        """

        when:
        configurationCacheRun("-Dfoo=v1", "printProperty")

        then:
        configurationCache.assertStateStored()
        outputContains("captured = mutated")

        when:
        configurationCacheRun("-Dfoo=different", "printProperty")

        then:
        configurationCache.assertStateLoaded()
        outputContains("captured = mutated")
    }

    @Issue("https://github.com/gradle/gradle/pull/37526")
    def "#mutation on clone still tracks the old value because it may be returned to the caller"() {
        given:
        buildFile """
            def clone = (Properties) System.getProperties().clone()
            clone.${mutation}
            def captured = clone.getProperty("foo")
            tasks.register("printProperty") {
                doLast { println("captured = \${captured}") }
            }
        """

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

        when:
        configurationCacheRun("-Dfoo=different", "printProperty")

        then:
        configurationCache.assertStateStored()
        outputContains("captured = mutated")

        where:
        mutation << [
            'setProperty("foo", "mutated")',
            'put("foo", "mutated")',
            'replace("foo", "mutated")',
            'compute("foo", { k, v -> "mutated" })',
            'computeIfPresent("foo", { k, v -> "mutated" })',
            'merge("foo", "mutated", { a, b -> "mutated" })',
        ]
    }

    def "untouched key read on clone is still tracked after mutating another key"() {
        given:
        buildFile """
            def clone = (Properties) System.getProperties().clone()
            clone.setProperty("touched", "mutated")
            def captured = clone.getProperty("untouched")
            tasks.register("printProperty") {
                doLast { println("untouched = \${captured}") }
            }
        """

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
        buildFile """
            def clone = (Properties) System.getProperties().clone()
            clone.setProperty("clone.only.prop", "clonevalue")

            tasks.register("printProperty") {
                doLast {
                    println("clone.only.prop in task = \${System.getProperty("clone.only.prop")}")
                }
            }
        """

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
        buildFile """
            def clone = (Properties) System.getProperties().clone()
            clone.remove("should.survive")

            tasks.register("printProperty") {
                doLast {
                    println("should.survive in task = \${System.getProperty("should.survive")}")
                }
            }
        """

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
        buildFile """
            def clone = (Properties) System.getProperties().clone()
            clone.clear()

            tasks.register("printProperty") {
                doLast {
                    println("user.home in task = \${System.getProperty("user.home")}")
                }
            }
        """

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
        buildFile """
            def clone = (Properties) System.getProperties().clone()
            clone.keySet().remove("should.survive.keyset")
            clone.entrySet().removeIf { it.key == "should.survive.entryset" }

            tasks.register("printProperty") {
                doLast {
                    println("keyset.prop = \${System.getProperty("should.survive.keyset")}")
                    println("entryset.prop = \${System.getProperty("should.survive.entryset")}")
                }
            }
        """

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
        buildFile """
            def beforeSet = System.getProperty("Hello")
            System.setProperty("Hello", "World")
            def copy = (Properties) System.getProperties().clone()
            copy.setProperty("Hello", "Bug")
            def realAfterCloneMutation = System.getProperty("Hello")
            tasks.register("printProperty") {
                doLast {
                    println("beforeSet = \${beforeSet}")
                    println("realAfterCloneMutation = \${realAfterCloneMutation}")
                }
            }
        """

        when:
        configurationCacheRun("printProperty")

        then:
        configurationCache.assertStateStored()
        outputContains("beforeSet = null")
        outputContains("realAfterCloneMutation = World")

        when:
        configurationCacheRun("printProperty")

        then:
        configurationCache.assertStateLoaded()
        outputContains("realAfterCloneMutation = World")
    }

    def "real System.setProperty is still tracked correctly when clone is also used"() {
        given:
        buildFile """
            def clone = (Properties) System.getProperties().clone()
            println("clone.foo = \${clone.getProperty("foo")}")
            System.setProperty("foo", "set-by-build")

            tasks.register("printProperty") {
                doLast {
                    println("foo in task = \${System.getProperty("foo")}")
                }
            }
        """

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

    def "clone of a clone of SystemProperties after parent mutation observes parent-mutated value and read is not tracked"() {
        given:
        buildFile """
            def clone = (Properties) System.getProperties().clone()
            clone.putAll([foo: 'baz'])
            def cloneOfClone = (Properties) clone.clone()
            def captured = cloneOfClone.getProperty('foo')
            tasks.register("printProperty") {
                doLast { println("captured = \${captured}") }
            }
        """

        when:
        configurationCacheRun("-Dfoo=bar", "printProperty")

        then:
        configurationCache.assertStateStored()
        outputContains("captured = baz")

        when:
        configurationCacheRun("-Dfoo=different", "printProperty")

        then:
        configurationCache.assertStateLoaded()
        outputContains("captured = baz")
    }

    def "clone of a clone of SystemProperties before parent mutation observes original value and read is tracked independently"() {
        given:
        buildFile """
            def clone = (Properties) System.getProperties().clone()
            def cloneOfClone = (Properties) clone.clone()
            clone.putAll([foo: 'baz'])
            def captured = cloneOfClone.getProperty('foo')
            tasks.register("printProperty") {
                doLast { println("captured = \${captured}") }
            }
        """

        when:
        configurationCacheRun("-Dfoo=bar", "printProperty")

        then:
        configurationCache.assertStateStored()
        outputContains("captured = bar")

        when: 'same -Dfoo: cache hits'
        configurationCacheRun("-Dfoo=bar", "printProperty")

        then:
        configurationCache.assertStateLoaded()
        outputContains("captured = bar")

        when:
        configurationCacheRun("-Dfoo=different", "printProperty")

        then:
        configurationCache.assertStateStored()
        outputContains("captured = different")
    }
}
