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

package org.gradle.api.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.LocalBuildCacheFixture

class CachedTaskExecutionErrorHandlingIntegrationTest extends AbstractIntegrationSpec implements LocalBuildCacheFixture {

    def setup() {
        settingsFile << """
            class FailingBuildCache extends AbstractBuildCache {
                boolean shouldFail
            }
            
            class FailingBuildCacheServiceFactory implements BuildCacheServiceFactory<FailingBuildCache> {
                FailingBuildCacheService createBuildCacheService(FailingBuildCache configuration) {
                    return new FailingBuildCacheService(configuration.shouldFail)
                }
            }
            
            class FailingBuildCacheService implements BuildCacheService {
                boolean shouldFail
                
                FailingBuildCacheService(boolean shouldFail) {
                    this.shouldFail = shouldFail
                }
                
                @Override
                boolean load(BuildCacheKey key, BuildCacheEntryReader reader) throws BuildCacheException {
                    if (shouldFail) {
                        throw new BuildCacheException("Unable to read " + key)
                    } else {
                        return false
                    }
                }
    
                @Override
                void store(BuildCacheKey key, BuildCacheEntryWriter writer) throws BuildCacheException {
                    if (shouldFail) {
                        throw new BuildCacheException("Unable to write " + key)
                    }
                }
    
                @Override
                String getDescription() {
                    return "Failing cache backend"
                }
    
                @Override
                void close() throws IOException {
                }
            }
            
            buildCache {
                registerBuildCacheService(FailingBuildCache, FailingBuildCacheServiceFactory)
                
                local {
                    enabled = false
                }
                
                remote(FailingBuildCache) {
                    shouldFail = gradle.startParameter.systemPropertiesArgs.containsKey("fail")
                    push = true
                }
            }
        """

        buildFile << """
            apply plugin: "base"
            
            @CacheableTask
            class CustomTask extends DefaultTask {
                @OutputFile 
                File outputFile = new File(temporaryDir, "output.txt")
                
                @TaskAction
                void generate() {
                    outputFile.text = "done"
                }
            }
            
            task customTask(type: CustomTask)
            task anotherCustomTask(type: CustomTask)
            
            // All of our tests just run 'assemble' and we need several 
            // tasks that are cacheable.

            // CustomTask is a dummy cacheable task that will cause
            // enough requests to the build cache to trip our short circuiting if
            // there are errors.
            assemble.dependsOn customTask, anotherCustomTask
        """
    }

    def "cache switches off after third error for the current build"() {
        // We require a distribution here so that we can capture
        // the output produced after the build has finished
        executer.requireGradleDistribution()
        executer.withBuildCacheEnabled()
        executer.withStackTraceChecksDisabled()

        when:
        succeeds "assemble", "-Dfail"
        then:
        output.count("Could not load entry") == 2
        output.count("Could not store entry") == 1
        output.count("The remote build cache is now disabled because 3 errors were encountered") == 1
        output.count("The remote build cache was disabled during the build after encountering 3 errors.") == 1

        expect:
        succeeds "clean"

        when:
        succeeds "assemble"
        then:
        !output.contains("The remote build cache is now disabled")
        !output.contains("The remote build cache was disabled during the build")
    }
}
