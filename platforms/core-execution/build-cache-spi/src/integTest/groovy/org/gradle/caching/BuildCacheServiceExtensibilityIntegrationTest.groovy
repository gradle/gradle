/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.caching

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class BuildCacheServiceExtensibilityIntegrationTest extends AbstractIntegrationSpec {
    def "can use custom build cache service"() {
        settingsFile << configureCustomBuildCacheService()
        buildFile << """
            apply plugin: "java"
        """
        file("src/main/java/Main.java") << """
            public class Main {}
        """

        when:
        withBuildCache().run "assemble"
        then:
        outputContains "Loading "
        outputContains "Storing "
    }

    def "produces deprecation message when BuildCacheKey.getDisplayName() is called"() {
        settingsFile << configureCustomBuildCacheService("println \"Dosplay name: \${key.displayName}\"")
        buildFile << """
            apply plugin: "java"
        """
        file("src/main/java/Main.java") << """
            public class Main {}
        """

        when:
        executer.expectDeprecationWarning("The BuildCacheKey.getDisplayName() method has been deprecated. This is scheduled to be removed in Gradle 9.0. Please use the getHashCode() method instead.")
        executer.expectDeprecationWarning("The BuildCacheKey.getDisplayName() method has been deprecated. This is scheduled to be removed in Gradle 9.0. Please use the getHashCode() method instead.")
        withBuildCache().run "assemble"
        then:
        noExceptionThrown()
    }

    private static String configureCustomBuildCacheService(String additionalLogic = "") {
        """
            class CustomBuildCache extends AbstractBuildCache {
                String shouldFail
            }

            class CustomBuildCacheServiceFactory implements BuildCacheServiceFactory<CustomBuildCache> {
                CustomBuildCacheService createBuildCacheService(CustomBuildCache configuration, Describer describer) {
                    return new CustomBuildCacheService()
                }
            }

            class CustomBuildCacheService implements BuildCacheService {
                @Override
                boolean load(BuildCacheKey key, BuildCacheEntryReader reader) throws BuildCacheException {
                    println "Loading \$key"
                    $additionalLogic
                    return false
                }

                @Override
                void store(BuildCacheKey key, BuildCacheEntryWriter writer) throws BuildCacheException {
                    println "Storing \$key"
                    $additionalLogic
                }

                @Override
                void close() throws IOException {
                }
            }

            buildCache {
                registerBuildCacheService(CustomBuildCache, CustomBuildCacheServiceFactory)

                local {
                    enabled = false
                }

                remote(CustomBuildCache) {
                    push = true
                }
            }
        """
    }
}
