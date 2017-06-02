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

package org.gradle.caching.internal

import org.gradle.caching.BuildCacheException
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.integtests.fixtures.DirectoryBuildCacheFixture
import org.gradle.internal.io.NullOutputStream
import spock.lang.Unroll

@Unroll
class BuildCacheBuildOperationsIntegrationTest extends AbstractIntegrationSpec implements DirectoryBuildCacheFixture {

    String localCacheClass = "LocalBuildCache"
    String remoteCacheClass = "RemoteBuildCache"

    def operations = new BuildOperationsFixture(executer, testDirectoryProvider)

    void local(String loadBody, String storeBody) {
        register(localCacheClass, loadBody, storeBody)
    }

    void remote(String loadBody, String storeBody) {
        register(remoteCacheClass, loadBody, storeBody)
    }

    def setup() {
        executer.beforeExecute { it.withBuildCacheEnabled() }
    }

    void register(String className, String loadBody, String storeBody) {
        settingsFile << """
            class ${className} extends AbstractBuildCache {}
            class ${className}ServiceFactory implements BuildCacheServiceFactory<${className}> {
                ${className}Service createBuildCacheService(${className} configuration, Describer describer) {
                    return new ${className}Service(configuration)
                }
            }
            class ${className}Service implements BuildCacheService {
                ${className}Service(${className} configuration) {
                }
    
                @Override
                boolean load(BuildCacheKey key, BuildCacheEntryReader reader) throws BuildCacheException {
                    ${loadBody ?: ""}
                }
    
                @Override
                void store(BuildCacheKey key, BuildCacheEntryWriter writer) throws BuildCacheException {
                    ${storeBody ?: ""}
                }
    
                @Override
                void close() throws IOException {
                }
            }

            buildCache {
                registerBuildCacheService(${className}, ${className}ServiceFactory)
            }
        """
    }

    String cacheableTask() {
        """
            @CacheableTask
            class CustomTask extends DefaultTask {
            
                @Input
                String val = "foo"
                
                @Input
                List<String> paths = []
                 
                @OutputDirectory
                File dir = project.file("build/dir")

                @TaskAction
                void generate() {
                    paths.each {
                        def f = new File(dir, it)
                        f.parentFile.mkdirs()
                        f.text = val
                    }
                }
            }

        """
    }

    def "emits operations for load and store"() {
        when:
        buildFile << cacheableTask() << """
            apply plugin: "base"
            tasks.create("t", CustomTask).paths << "out1" << "out2"
        """

        succeeds("t")

        then:
        def missLoadOp = operations.only(BuildCacheLoadBuildOperationType)
        missLoadOp.details.cacheKey != null
        missLoadOp.details.role == "local"
        missLoadOp.result.archiveSize == 0
        missLoadOp.result.archiveEntryCount == 0

        def storeOp = operations.only(BuildCacheStoreBuildOperationType)
        storeOp.details.cacheKey == missLoadOp.details.cacheKey
        storeOp.details.role == "local"
        storeOp.result.archiveSize == localCacheArtifact(storeOp.details.cacheKey.toString()).length()
        storeOp.result.archiveEntryCount == 4

        operations.orderedSerialSiblings(missLoadOp, storeOp)

        when:
        succeeds("clean", "t")

        then:
        def hitLoadOp = operations.only(BuildCacheLoadBuildOperationType)
        hitLoadOp.details.cacheKey == storeOp.details.cacheKey
        hitLoadOp.details.role == "local"
        hitLoadOp.result.archiveSize == localCacheArtifact(hitLoadOp.details.cacheKey.toString()).length()
        hitLoadOp.result.archiveEntryCount == 4
        operations.none(BuildCacheStoreBuildOperationType)
    }

    def "records load failure"() {
        when:
        local("throw new ${exceptionType.name}('!')", "writer.writeTo(new ${NullOutputStream.name}())")
        settingsFile << """
            buildCache { local($localCacheClass) }
        """
        buildFile << cacheableTask() << """
            apply plugin: "base"
            tasks.create("t", CustomTask).paths << "out1" << "out2"
        """

        executer.withStackTraceChecksDisabled()
        if (exceptionType == BuildCacheException) {
            succeeds("t")
        } else {
            fails("t")
        }

        then:
        def failedLoadOp = operations.only(BuildCacheLoadBuildOperationType)
        failedLoadOp.details.cacheKey != null
        failedLoadOp.details.role == "local"
        failedLoadOp.result == null
        failedLoadOp.failure == "${exceptionType.name}: !"

        where:
        exceptionType << [RuntimeException, BuildCacheException, IOException]
    }


    def "records store failure"() {
        when:
        local("", "throw new ${exceptionType.name}('!')")
        settingsFile << """
            buildCache { local($localCacheClass) }
        """
        buildFile << cacheableTask() << """
            apply plugin: "base"
            tasks.create("t", CustomTask).paths << "out1" << "out2"
        """

        executer.withStackTraceChecksDisabled()
        succeeds("t")

        then:
        def failedLoadOp = operations.only(BuildCacheStoreBuildOperationType)
        failedLoadOp.details.cacheKey != null
        failedLoadOp.details.role == "local"
        failedLoadOp.result == null
        failedLoadOp.failure == "${exceptionType.name}: !"

        where:
        exceptionType << [RuntimeException, BuildCacheException, IOException]
    }

    def "records ops when two caches used"() {
        given:
        remote("", "writer.writeTo(new ${NullOutputStream.name}())")

        settingsFile << """
            buildCache {
                remote($remoteCacheClass) {
                    push = true
                }   
            }
        """

        buildFile << cacheableTask() << """
            apply plugin: "base"
            tasks.create("t", CustomTask).paths << "out1" << "out2"
        """

        when:
        succeeds("t")

        then:
        def localMissLoadOp = operations.only(BuildCacheLoadBuildOperationType) {
            it.details.role == "local"
        }
        def remoteMissLoadOp = operations.only(BuildCacheLoadBuildOperationType) {
            it.details.role == "remote"
        }
        def localStoreOp = operations.only(BuildCacheStoreBuildOperationType) {
            it.details.role == "local"
        }
        def remoteStoreOp = operations.only(BuildCacheStoreBuildOperationType) {
            it.details.role == "remote"
        }

        localStoreOp.details.cacheKey == remoteStoreOp.details.cacheKey
        localStoreOp.result.archiveSize == localCacheArtifact(localStoreOp.details.cacheKey.toString()).length()
        localStoreOp.result.archiveEntryCount == 4
        remoteStoreOp.result.archiveSize == localStoreOp.result.archiveSize
        remoteStoreOp.result.archiveEntryCount == localStoreOp.result.archiveEntryCount

        operations.orderedSerialSiblings(localMissLoadOp, remoteMissLoadOp, localStoreOp, remoteStoreOp)
    }

}
