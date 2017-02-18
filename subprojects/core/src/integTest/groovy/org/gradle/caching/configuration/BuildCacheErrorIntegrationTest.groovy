/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.caching.configuration

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class BuildCacheErrorIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        settingsFile << """
            class TestBuildCache extends AbstractBuildCache {}
            class TestBuildCacheServiceFactory implements BuildCacheServiceFactory {
                Class getConfigurationType() { TestBuildCache }
                BuildCacheService build(org.gradle.caching.configuration.BuildCache configuration) { null }
            }
        """
    }
    def "registering a null build cache service factory fails"() {
        settingsFile << """
            buildCache {
                registerBuildCacheServiceFactory(null)
            }
        """
        when:
        fails("help")
        then:
        result.error.contains("You cannot register a null build cache service factory.")
    }

    def "can register build cache service factory multiple times and last one wins"() {
        settingsFile << """
            buildCache {
                registerBuildCacheServiceFactory(new TestBuildCacheServiceFactory())
                
                def expected = new TestBuildCacheServiceFactory()
                registerBuildCacheServiceFactory(expected)
                
                assert getFactory(new TestBuildCache()) == expected
            }
        """
        expect:
        succeeds("help")
    }

    def "attempting to configure a unknown remote build cache fails with a reasonable error"() {
        settingsFile << """
            buildCache {
                remote {
                    assert false : "should not happen"
                }
            }
        """
        when:
        fails("help")
        then:
        result.error.contains("A type for the remote build cache must be configured first.")
    }

    def "attempting to replace an existing remote build cache with a different type fails"() {
        settingsFile << """
            buildCache {
                remote(HttpBuildCache)
                remote(TestBuildCache) {
                    assert false : "should not happen"
                }
            }
        """
        when:
        fails("help")
        then:
        result.error.contains("The given remote build cache type 'TestBuildCache' does not match the already configured type 'org.gradle.caching.http.HttpBuildCache'.")
    }

    def "attempting to use an unknown build cache type fails with a reasonable message"() {
        buildFile << """
            apply plugin: 'java'
        """
        file("src/main/java/Main.java") << "class Main {}"
        settingsFile << """
            buildCache {
                remote(TestBuildCache)
            }
        """
        when:
        executer.withBuildCacheEnabled()
        fails("compileJava")
        then:
        result.error.contains("No build cache service factory for type 'TestBuildCache' could be found.")
    }
}
