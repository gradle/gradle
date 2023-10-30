/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.configurationcache


class ConfigurationCacheCircularReferenceIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def 'circular reference without hashCode override is no problem'() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        buildFile '''

            class Circular {
                private final String a
                def direct = new HashSet<Object>()
                def indirect = new Indirect()
                private final String z

                Circular(String a, String z) {
                    this.a = a
                    this.z = z
                }

                String toString() {
                    "$a:$z"
                }
            }

            class Indirect {
                def references = new HashSet<Object>()
            }

            def circular = new Circular('foo', 'bar')
            circular.direct.add(circular)
            circular.indirect.references.add(circular)

            tasks.register('circular') {
                doLast {
                    def direct = circular.direct
                    def indirect = circular.indirect.references
                    println('toString() => ' + circular)
                    println('circular in direct => ' + (circular in direct))
                    println('circular in indirect => ' + (circular in indirect))
                    println('Circular(foo, bar) in direct => ' + (new Circular('foo', 'bar') in direct))
                    println('Circular(foo, bar) in indirect => ' + (new Circular('foo', 'bar') in indirect))
                    println('circular === direct.first() => ' + (circular === direct.first()))
                    println('circular === indirect.first() => ' + (circular === indirect.first()))
                }
            }
        '''

        when:
        configurationCacheRun 'circular'

        then:
        configurationCache.assertStateStored()
        problems.assertResultHasProblems(result) {
            withTotalProblemsCount(0)
        }

        when:
        configurationCacheRun 'circular'

        then:
        configurationCache.assertStateLoaded()

        and:
        outputContains 'toString() => foo:bar'
        outputContains 'circular in direct => true'
        outputContains 'circular in indirect => true'
        outputContains 'Circular(foo, bar) in direct => false'
        outputContains 'Circular(foo, bar) in indirect => false'
        outputContains 'circular === direct.first() => true'
        outputContains 'circular === indirect.first() => true'
    }

    def 'circular reference in HashSet is fully initialized prior to insertion but problems are reported'() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        buildFile '''

            class Circular {
                private final String a
                def direct = new HashSet<Object>()
                def indirect = new Indirect()
                private final String z

                Circular(String a, String z) {
                    this.a = a
                    this.z = z
                }

                int hashCode() {
                    assert a != null && z != null
                    a.hashCode() ^ z.hashCode()
                }

                boolean equals(Object other) {
                    assert a != null && z != null
                    (other instanceof Circular) && a == other.a && z == other.z
                }

                String toString() {
                    "$a:$z"
                }
            }

            class Indirect {
                def references = new HashSet<Object>()
            }

            def circular = new Circular('foo', 'bar')
            circular.direct.add(circular)
            circular.indirect.references.add(circular)

            tasks.register('circular') {
                doLast {
                    def direct = circular.direct
                    def indirect = circular.indirect.references
                    println('toString() => ' + circular)
                    println('circular in direct => ' + (circular in direct))
                    println('circular in indirect => ' + (circular in indirect))
                    println('Circular(foo, bar) in direct => ' + (new Circular('foo', 'bar') in direct))
                    println('Circular(foo, bar) in indirect => ' + (new Circular('foo', 'bar') in indirect))
                    println('circular === direct.first() => ' + (circular === direct.first()))
                    println('circular === indirect.first() => ' + (circular === indirect.first()))
                }
            }
        '''

        when:
        configurationCacheRunLenient 'circular'

        then:
        configurationCache.assertStateStored()
        problems.assertResultHasProblems(result) {
            withUniqueProblems("Task `:circular` of type `org.gradle.api.DefaultTask`: Circular references can lead to undefined behavior upon deserialization.")
            withTotalProblemsCount(2)
            withProblemsWithStackTraceCount(0)
        }

        when:
        configurationCacheRunLenient 'circular'

        then:
        configurationCache.assertStateLoaded()

        and:
        outputContains 'toString() => foo:bar'
        outputContains 'circular in direct => true'
        outputContains 'circular in indirect => true'
        outputContains 'Circular(foo, bar) in direct => true'
        outputContains 'Circular(foo, bar) in indirect => true'
        outputContains 'circular === direct.first() => true'
        outputContains 'circular === indirect.first() => true'
    }
}
