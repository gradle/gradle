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

package org.gradle.caching.configuration.internal

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class BuildCacheErrorIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        settingsFile << """
            class TestBuildCache extends AbstractBuildCache {}
            class TestBuildCacheServiceFactory implements BuildCacheServiceFactory<TestBuildCache> {
                TestBuildCacheService createBuildCacheService(TestBuildCache configuration, Describer describer) {
                    return new TestBuildCacheService(configuration)
                }
            }
            class TestBuildCacheService implements BuildCacheService {
                TestBuildCacheService(TestBuildCache configuration) {
                }
                
                @Override
                boolean load(BuildCacheKey key, BuildCacheEntryReader reader) throws BuildCacheException {
                    return false
                }
    
                @Override
                void store(BuildCacheKey key, BuildCacheEntryWriter writer) throws BuildCacheException {
                }
    
                @Override
                void close() throws IOException {
                }
            }
        """
    }
    def "registering with null fails"() {
        given:
        settingsFile << """
            buildCache {
                registerBuildCacheService(null, TestBuildCacheServiceFactory)
            }
        """
        when:
        fails("help")
        then:
        failure.assertHasCause("configurationType cannot be null.")

        when:
        settingsFile.text = settingsFile.text.replace("registerBuildCacheService(null, TestBuildCacheServiceFactory)", "registerBuildCacheService(TestBuildCache, null)")
        and:
        fails("help")
        then:
        failure.assertHasCause("buildCacheServiceFactoryType cannot be null.")
    }

    def "can register build cache service factory multiple times and last one wins"() {
        settingsFile << """
            buildCache {
                registerBuildCacheService(TestBuildCache, TestBuildCacheServiceFactory)
                registerBuildCacheService(TestBuildCache, TestBuildCacheServiceFactory)
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
        failure.assertHasCause("A type for the remote build cache must be configured first.")
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
        failure.assertHasCause("Build cache type 'TestBuildCache' has not been registered.")
    }
}
