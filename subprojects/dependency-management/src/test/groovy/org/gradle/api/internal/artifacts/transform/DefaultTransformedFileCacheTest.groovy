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

package org.gradle.api.internal.artifacts.transform

import com.google.common.hash.HashCode
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCacheMetaData
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshot
import org.gradle.api.internal.changedetection.state.FileSystemSnapshotter
import org.gradle.api.internal.changedetection.state.InMemoryCacheDecoratorFactory
import org.gradle.cache.internal.CacheScopeMapping
import org.gradle.cache.internal.DefaultCacheRepository
import org.gradle.caching.internal.BuildCacheHasher
import org.gradle.internal.util.BiFunction
import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testfixtures.internal.InMemoryCacheFactory
import org.gradle.util.UsesNativeServices
import org.junit.Rule

@UsesNativeServices
class DefaultTransformedFileCacheTest extends ConcurrentSpec {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    def artifactCacheMetaData = Mock(ArtifactCacheMetaData)
    def scopeMapping = Stub(CacheScopeMapping)
    def cacheRepo = new DefaultCacheRepository(scopeMapping, new InMemoryCacheFactory())
    def decorator = Stub(InMemoryCacheDecoratorFactory)
    def snapshotter = Mock(FileSystemSnapshotter)
    def cache

    def setup() {
        scopeMapping.getBaseDirectory(_, _, _) >> tmpDir.testDirectory
        scopeMapping.getRootDirectory(_) >> tmpDir.testDirectory
        artifactCacheMetaData.transformsStoreDirectory >> tmpDir.file("output")
        cache = new DefaultTransformedFileCache(artifactCacheMetaData, cacheRepo, decorator, snapshotter)
    }

    def "reuses result for given inputs and transform"() {
        def transform = Mock(BiFunction)
        def inputFile = tmpDir.file("a")

        when:
        def result = cache.getResult(inputFile, HashCode.fromInt(123), transform)

        then:
        result*.name == ["a.1"]

        and:
        1 * snapshotter.snapshotAll(inputFile) >> snapshot(HashCode.fromInt(234))
        1 * transform.apply(inputFile, _) >>  { File file, File dir -> def r = new File(dir, "a.1"); r.text = "result"; [r] }
        0 * snapshotter._
        0 * transform._

        when:
        def result2 = cache.getResult(inputFile, HashCode.fromInt(123), transform)

        then:
        result2 == result

        and:
        1 * snapshotter.snapshotAll(inputFile) >> snapshot(HashCode.fromInt(234))
        0 * snapshotter._
        0 * transform._
    }

    def "reuses result when transform returns its input file"() {
        def transform = Mock(BiFunction)
        def inputFile = tmpDir.file("a").createFile()

        when:
        def result = cache.getResult(inputFile, HashCode.fromInt(123), transform)

        then:
        result == [inputFile]

        and:
        1 * snapshotter.snapshotAll(inputFile) >> snapshot(HashCode.fromInt(234))
        1 * transform.apply(inputFile, _) >>  { File file, File dir -> [file] }
        0 * snapshotter._
        0 * transform._

        when:
        def result2 = cache.getResult(inputFile, HashCode.fromInt(123), transform)

        then:
        result2 == result

        and:
        1 * snapshotter.snapshotAll(inputFile) >> snapshot(HashCode.fromInt(234))
        0 * transform._
        0 * snapshotter._
    }

    def "applies transform once when requested concurrently by multiple threads"() {
        def transform = Mock(BiFunction)
        def inputFile = tmpDir.file("a")

        when:
        def result1
        def result2
        def result3
        def result4
        async {
            start {
                result1 = cache.getResult(inputFile, HashCode.fromInt(123), transform)
            }
            start {
                result2 = cache.getResult(inputFile, HashCode.fromInt(123), transform)
            }
            start {
                result3 = cache.getResult(inputFile, HashCode.fromInt(123), transform)
            }
            start {
                result4 = cache.getResult(inputFile, HashCode.fromInt(123), transform)
            }
        }

        then:
        result1*.name == ["a.1"]
        result2 == result1
        result3 == result1
        result4 == result1

        and:
        4 * snapshotter.snapshotAll(inputFile) >> snapshot(HashCode.fromInt(234))
        1 * transform.apply(inputFile, _) >> { File file, File dir -> def r = new File(dir, "a.1"); r.text = "result"; [r] }
        0 * transform._
    }

    def "multiple threads can transform files concurrently"() {
        when:
        async {
            start {
                cache.getResult(new File("a"), HashCode.fromInt(123)) { file, outDir ->
                    instant.a
                    thread.blockUntil.b
                    instant.a_done
                    [file]
                }
            }
            start {
                cache.getResult(new File("b"), HashCode.fromInt(345)) { file, outDir ->
                    instant.b
                    thread.blockUntil.a
                    instant.b_done
                    [file]
                }
            }
        }

        then:
        instant.a_done > instant.b
        instant.b_done > instant.a

        and:
        2 * snapshotter.snapshotAll(_) >>> [snapshot(HashCode.fromInt(234)), snapshot(HashCode.fromInt(456))]
    }

    def "does not reuse result when transform inputs are different"() {
        def transform1 = Mock(BiFunction)
        def transform2 = Mock(BiFunction)
        def inputFile = tmpDir.file("a")

        given:
        _ * transform1.apply(inputFile, _) >> { File file, File dir -> def r = new File(dir, "a.1"); r.text = "result"; [r] }
        _ * snapshotter.snapshotAll(inputFile) >> snapshot(HashCode.fromInt(234))

        cache.getResult(inputFile, HashCode.fromInt(123), transform1)

        when:
        def result = cache.getResult(inputFile, HashCode.fromInt(234), transform2)

        then:
        result*.name == ["a.2"]

        and:
        1 * transform2.apply(inputFile, _) >>  { File file, File dir -> def r = new File(dir, "a.2"); r.text = "result"; [r] }
        0 * transform1._
        0 * transform2._

        when:
        def result2 = cache.getResult(inputFile, HashCode.fromInt(123), transform1)
        def result3 = cache.getResult(inputFile, HashCode.fromInt(234), transform2)

        then:
        result2*.name == ["a.1"]
        result3 == result

        and:
        0 * transform1._
        0 * transform2._
    }

    def "does not reuse result when transform input files have different content"() {
        def transform1 = Mock(BiFunction)
        def transform2 = Mock(BiFunction)
        def inputFile = tmpDir.file("a")

        given:
        _ * transform1.apply(inputFile, _) >> { File file, File dir -> def r = new File(dir, "a.1"); r.text = "result"; [r] }
        _ * snapshotter.snapshotAll(inputFile) >> snapshot(HashCode.fromInt(234))

        cache.getResult(inputFile, HashCode.fromInt(123), transform1)

        when:
        def result = cache.getResult(inputFile, HashCode.fromInt(123), transform2)

        then:
        result*.name == ["a.2"]

        and:
        1 * snapshotter.snapshotAll(inputFile) >> snapshot(HashCode.fromInt(456))
        1 * transform2.apply(inputFile, _) >>  { File file, File dir -> def r = new File(dir, "a.2"); r.text = "result"; [r] }
        0 * transform1._
        0 * transform2._

        when:
        def result2 = cache.getResult(inputFile, HashCode.fromInt(123), transform1)
        def result3 = cache.getResult(inputFile, HashCode.fromInt(123), transform2)

        then:
        result2*.name == ["a.1"]
        result3 == result

        and:
        2 * snapshotter.snapshotAll(inputFile) >>> [snapshot(HashCode.fromInt(234)), snapshot(HashCode.fromInt(456))]
        0 * transform1._
        0 * transform2._
    }

    def "runs transform when previous execution failed and cleans up directory"() {
        def transform = Mock(BiFunction)
        def failure = new RuntimeException()
        def inputFile = tmpDir.file("a")

        when:
        cache.getResult(inputFile, HashCode.fromInt(123), transform)

        then:
        def e = thrown(RuntimeException)
        e.is(failure)

        and:
        1 * snapshotter.snapshotAll(inputFile) >> snapshot(HashCode.fromInt(456))
        1 * transform.apply(inputFile, _) >>  { File file, File dir ->
            dir.mkdirs()
            new File(dir, "delete-me").text = "broken"
            throw failure
        }
        0 * transform._

        when:
        def result = cache.getResult(inputFile, HashCode.fromInt(123), transform)

        then:
        result*.name == ["a.1"]

        and:
        1 * snapshotter.snapshotAll(inputFile) >> snapshot(HashCode.fromInt(456))
        1 * transform.apply(inputFile, _) >>  { File file, File dir ->
            assert dir.list().length == 0
            def r = new File(dir, "a.1")
            r.text = "result"
            [r]
        }
        0 * transform._
    }

    def "runs transform when output has been removed"() {
        def transform = Mock(BiFunction)
        def inputFile = tmpDir.file("a")

        given:
        1 * snapshotter.snapshotAll(inputFile) >> snapshot(HashCode.fromInt(456))
        1 * transform.apply(inputFile, _) >>  { File file, File dir -> def r = new File(dir, "a.1"); r.text = "result"; [r] }

        def result = cache.getResult(inputFile, HashCode.fromInt(123), transform)

        when:
        def cache = new DefaultTransformedFileCache(artifactCacheMetaData, cacheRepo, decorator, snapshotter)
        result.first().delete()
        def result2 = cache.getResult(inputFile, HashCode.fromInt(123), transform)

        then:
        result2 == result

        and:
        1 * snapshotter.snapshotAll(inputFile) >> snapshot(HashCode.fromInt(456))
        1 * transform.apply(inputFile, _) >>  { File file, File dir -> def r = new File(dir, "a.1"); r.text = "result"; [r] }
        0 * transform._
    }

    def snapshot(HashCode hashCode) {
        FileCollectionSnapshot snapshot = Stub(FileCollectionSnapshot)
        snapshot.appendToHasher(_) >> { BuildCacheHasher hasher -> hasher.putBytes(hashCode.asBytes()) }
        snapshot
    }
}
