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

package org.gradle.caching.internal

import org.gradle.api.internal.file.DefaultTemporaryFileProvider
import org.gradle.caching.BuildCacheEntryReader
import org.gradle.caching.BuildCacheEntryWriter
import org.gradle.caching.BuildCacheKey
import org.gradle.internal.Factory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class StagingBuildCacheServiceDecoratorTest extends Specification {
    @Rule
    final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()

    def key = Mock(BuildCacheKey)
    def reader = Mock(BuildCacheEntryReader)
    def writer = Mock(BuildCacheEntryWriter)
    def delegate = Mock(RoleAwareBuildCacheService)

    def stageDir = temporaryFolder.createDir("tmp")
    def temporaryFileProvider = new DefaultTemporaryFileProvider(new Factory<File>() {
        @Override
        File create() {
            return stageDir
        }
    })

    def "delegates to delegate when not staging"() {
        def decorator = new StagingBuildCacheServiceDecorator(temporaryFileProvider, false, delegate)

        when:
        decorator.close()
        then:
        1 * delegate.close()

        when:
        decorator.getDescription()
        then:
        1 * delegate.getDescription()

        when:
        decorator.store(key, writer)
        then:
        1 * delegate.store(key, writer)

        when:
        decorator.load(key, reader)
        then:
        1 * delegate.load(key, reader)
    }

    def "delegates to delegate when staging"() {
        def decorator = new StagingBuildCacheServiceDecorator(temporaryFileProvider, true, delegate)

        when:
        decorator.close()
        then:
        1 * delegate.close()

        when:
        decorator.getDescription()
        then:
        1 * delegate.getDescription()

        when:
        decorator.store(key, writer)
        then:
        1 * delegate.store(key, _)

        when:
        decorator.load(key, reader)
        then:
        1 * delegate.load(key, _)
    }

    def "staged file is used when loading cache entries"() {
        def decorator = new StagingBuildCacheServiceDecorator(temporaryFileProvider, true, delegate)
        delegate.load(_, _) >> { BuildCacheKey key, BuildCacheEntryReader reader ->
            reader.readFrom(new ByteArrayInputStream("data".bytes))
            return true
        }
        expect:
        decorator.load(key, new BuildCacheEntryReader() {
            @Override
            void readFrom(InputStream input) throws IOException {
                assert !(input instanceof ByteArrayInputStream)
                assertHasStageFile()
            }
        })
    }

    def "staged file is used when storing cache entries"() {
        def decorator = new StagingBuildCacheServiceDecorator(temporaryFileProvider, true, delegate)
        delegate.store(_, _) >> { BuildCacheKey key, BuildCacheEntryWriter writer ->
            writer.writeTo(new ByteArrayOutputStream())
        }
        expect:
        decorator.store(key, new BuildCacheEntryWriter() {
            @Override
            void writeTo(OutputStream output) throws IOException {
                assert !(output instanceof ByteArrayOutputStream)
                assertHasStageFile()
            }
        })
    }

    private void assertHasStageFile() {
        assert stageDir.allDescendants().find { it.startsWith("gradle_cache") && it.endsWith("entry") }
    }

    def cleanup() {
        stageDir.assertIsEmptyDir()
    }
}
