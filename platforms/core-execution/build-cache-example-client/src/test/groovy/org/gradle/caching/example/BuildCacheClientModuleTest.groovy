/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.caching.example

import com.google.common.collect.ImmutableMap
import com.google.inject.Guice
import org.gradle.caching.internal.CacheableEntity
import org.gradle.caching.internal.SimpleBuildCacheKey
import org.gradle.caching.internal.controller.BuildCacheController
import org.gradle.internal.file.TreeType
import org.gradle.internal.hash.HashCode
import org.gradle.internal.vfs.FileSystemAccess
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import java.time.Duration

class BuildCacheClientModuleTest extends Specification {
    def injector = Guice.createInjector(new BuildCacheClientModule("build-1"))
    def buildCacheController = injector.getInstance(BuildCacheController)
    def fileSystemAccess = injector.getInstance(FileSystemAccess)

    def cacheKey = new SimpleBuildCacheKey(HashCode.fromString("b9800f9130db9efa58f6ec8c744f1cc7"))
    def identity = "test-entity"

    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(BuildCacheClientModuleTest)

    def "can handle missing entry"() {
        def outputDirectory = tmpDir.createDir("target-output")
        def entity = new ExampleEntity(identity, outputDirectory)

        when:
        def result = buildCacheController.load(cacheKey, entity)

        then:
        !result.isPresent()
    }

    def "can store and load outputs"() {
        // Produce some example output (simulate executing cacheable work in a temporary sandbox)
        def workOutputDirectory = tmpDir.createDir("work-output")
        def workOutputTxt = workOutputDirectory.file("output.txt")
        workOutputTxt.text = "contents"

        // Capture the snapshot of the produced output
        def producedOutputSnapshot = fileSystemAccess.read(workOutputDirectory.absolutePath)

        // Store the entity in the cache
        def entity = new ExampleEntity("test-entity", workOutputDirectory)

        when:
        buildCacheController.store(
            cacheKey,
            entity,
            ImmutableMap.of("output", producedOutputSnapshot),
            Duration.ofSeconds(10))

        then:
        noExceptionThrown()

        when:
        // Load the entity from the cache
        def targetOutputDirectory = tmpDir.createDir("target-output")
        def targetEntity = new ExampleEntity(identity, targetOutputDirectory)
        def loadResult = buildCacheController.load(cacheKey, targetEntity)

        then:
        loadResult.isPresent()
        targetOutputDirectory.assertHasDescendants("output.txt")
        targetOutputDirectory.file("output.txt").text == "contents"
    }

    static class ExampleEntity implements CacheableEntity {
        final String identity
        final File outputDirectory

        ExampleEntity(String identity, File outputDirectory) {
            this.identity = identity
            this.outputDirectory = outputDirectory
        }

        @Override
        Class<?> getType() {
            return getClass()
        }

        @Override
        String getDisplayName() {
            return identity
        }

        @Override
        void visitOutputTrees(CacheableTreeVisitor visitor) {
            visitor.visitOutputTree("output", TreeType.DIRECTORY, outputDirectory)
        }
    }
}
