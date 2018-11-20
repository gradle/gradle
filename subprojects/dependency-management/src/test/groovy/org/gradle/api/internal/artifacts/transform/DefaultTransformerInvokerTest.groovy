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

import org.gradle.api.artifacts.transform.ArtifactTransform
import org.gradle.api.internal.artifacts.ivyservice.CacheLayout
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.internal.classloader.ClassLoaderHierarchyHasher
import org.gradle.internal.execution.impl.DefaultWorkExecutor
import org.gradle.internal.execution.impl.steps.Context
import org.gradle.internal.execution.impl.steps.CreateOutputsStep
import org.gradle.internal.execution.impl.steps.Step
import org.gradle.internal.execution.impl.steps.UpToDateResult
import org.gradle.internal.hash.HashCode
import org.gradle.internal.snapshot.FileSystemSnapshotter
import org.gradle.internal.snapshot.RegularFileSnapshot
import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.UsesNativeServices
import org.junit.Rule
import spock.lang.Ignore

@UsesNativeServices
@Ignore("FIXME wolfs - rewrite and replace by better test")
class DefaultTransformerInvokerTest extends ConcurrentSpec {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    def transformsStoreDirectory = tmpDir.file("output")
    def snapshotter = Mock(FileSystemSnapshotter)
    def result = Mock(UpToDateResult)
    def artifactTransformListener = Mock(ArtifactTransformListener)
    def executeStep = new Step<Context, UpToDateResult>() {
        @Override
        UpToDateResult execute(Context context) {
            context.work.execute()
            return result
        }
    }
    def workExecutor = new DefaultWorkExecutor(new CreateOutputsStep(executeStep))
    def historyRepository = Mock(TransformerExecutionHistoryRepository)
    DefaultTransformerInvoker transformerInvoker

    def setup() {
        transformerInvoker = createInvoker()
    }

    private DefaultTransformerInvoker createInvoker() {
        new DefaultTransformerInvoker(workExecutor, snapshotter, artifactTransformListener, historyRepository, outputFileCollectionFingerprinter, Mock(ClassLoaderHierarchyHasher))
    }

    def "reuses result for given inputs and transform"() {
        def transformerRegistration = Mock(DefaultTransformer)
        def inputFile = tmpDir.file("a")

        when:
        def result = transformerInvoker.invoke(inputFile, transformerRegistration, subject)

        then:
        result*.name == ["a.1"]

        and:
        1 * transformerRegistration.secondaryInputHash >> HashCode.fromInt(123)
        1 * snapshotter.snapshot(inputFile) >> snapshot(HashCode.fromInt(234))
        1 * transformerRegistration.transform(inputFile, _) >>  { File file, File dir -> def r = new File(dir, "a.1"); r.text = "result"; [r] }
        1 * fileAccessTimeJournal.setLastAccessTime(_, _)
        0 * snapshotter._
        0 * transformerRegistration._

        when:
        def result2 = transformerInvoker.invoke(inputFile, transformerRegistration, subject, dependenciesProvider)

        then:
        result2 == result

        and:
        1 * transformerRegistration.secondaryInputHash >> HashCode.fromInt(123)
        1 * snapshotter.snapshot(inputFile) >> snapshot(HashCode.fromInt(234))
        0 * fileAccessTimeJournal.setLastAccessTime(_, _)
        0 * snapshotter._
        0 * transformerRegistration._
    }

    def "does not contain result before transform ran"() {
        given:
        def inputFile = tmpDir.file("a")
        def hash = HashCode.fromInt(123)
        def transformer = Stub(Transformer) {
            getSecondaryInputHash() >> hash
        }
        _ * snapshotter.snapshot(_) >> snapshot(hash)

        expect:
        !transformerInvoker.hasCachedResult(inputFile, transformer)
    }

    def "contains result after transform ran once"() {
        given:
        def transformerRegistration = Mock(DefaultTransformer)
        def inputFile = tmpDir.file("a")
        def hash = HashCode.fromInt(123)
        def transformer = Stub(Transformer) {
            getSecondaryInputHash() >> hash
        }
        _ * transformerRegistration.secondaryInputHash >> hash
        _ * snapshotter.snapshot(_) >> snapshot(hash)
        _ * transformerRegistration.transform(_, _) >>  { File file, File dir -> [new TestFile(dir, file.getName()).touch()] }

        when:
        transformerInvoker.invoke(inputFile, transformerRegistration, subject)

        then:
        transformerInvoker.hasCachedResult(inputFile, transformer)
    }

    def "does not contain result if a different transform ran"() {
        given:
        def transformerRegistration = Mock(DefaultTransformer)
        def inputFile = tmpDir.file("a")
        def hash = HashCode.fromInt(123)
        def otherHash = HashCode.fromInt(456)
        def otherTransformer = Stub(Transformer) {
            getSecondaryInputHash() >> otherHash
        }
        _ * transformerRegistration.secondaryInputHash >> hash
        _ * snapshotter.snapshot(_) >> snapshot(hash)
        _ * transformerRegistration.transform(_, _) >>  { File file, File dir -> [file] }

        when:
        transformerInvoker.invoke(inputFile, transformerRegistration, subject)

        then:
        !transformerInvoker.hasCachedResult(inputFile, otherTransformer)
    }

    def "reuses result when transform returns its input file"() {
        def transformerRegistration = Mock(DefaultTransformer)
        def inputFile = tmpDir.file("a").createFile()

        when:
        def result = transformerInvoker.invoke(inputFile, transformerRegistration, subject)

        then:
        result == [inputFile]

        and:
        1 * transformerRegistration.secondaryInputHash >> HashCode.fromInt(123)
        1 * snapshotter.snapshot(inputFile) >> snapshot(HashCode.fromInt(234))
        1 * transformerRegistration.transform(inputFile, _) >>  { File file, File dir -> [file] }
        0 * snapshotter._
        0 * transformerRegistration._

        when:
        def result2 = transformerInvoker.invoke(inputFile, transformerRegistration, subject, dependenciesProvider)

        then:
        result2 == result

        and:
        1 * transformerRegistration.secondaryInputHash >> HashCode.fromInt(123)
        1 * snapshotter.snapshot(inputFile) >> snapshot(HashCode.fromInt(234))
        0 * transformerRegistration._
        0 * snapshotter._
        0 * fileAccessTimeJournal._
    }

    def "applies transform once when requested concurrently by multiple threads"() {
        def transformerRegistration = Mock(DefaultTransformer)
        def inputFile = tmpDir.file("a")

        when:
        def result1
        def result2
        def result3
        def result4
        async {
            start {
                result1 = transformerInvoker.invoke(inputFile, transformerRegistration, subject)
            }
            start {
                result2 = transformerInvoker.invoke(inputFile, transformerRegistration, subject)
            }
            start {
                result3 = transformerInvoker.invoke(inputFile, transformerRegistration, subject)
            }
            start {
                result4 = transformerInvoker.invoke(inputFile, transformerRegistration, subject)
            }
        }

        then:
        result1*.name == ["a.1"]
        result2 == result1
        result3 == result1
        result4 == result1

        and:
        4 * transformerRegistration.secondaryInputHash >> HashCode.fromInt(123)
        4 * snapshotter.snapshot(inputFile) >> snapshot(HashCode.fromInt(234))
        1 * transformerRegistration.transform(inputFile, _) >> { File file, File dir -> def r = new File(dir, "a.1"); r.text = "result"; [r] }
        1 * fileAccessTimeJournal.setLastAccessTime(_, _)
        0 * transformerRegistration._
    }

    def "multiple threads can transform files concurrently"() {
        def transformerRegistrationA = new DefaultTransformer(ArtifactTransform.class, null, HashCode.fromInt(123), null, ImmutableAttributes.EMPTY) {
            @Override
            List<File> transform(File primaryInput, File outputDir, ArtifactTransformDependenciesProvider dependenciesProvider) {
                instant.a
                thread.blockUntil.b
                instant.a_done
                [new TestFile(outputDir, primaryInput.getName()).touch()]            }
        }
        def transformerRegistrationB = new DefaultTransformer(ArtifactTransform.class, null, HashCode.fromInt(345), null, ImmutableAttributes.EMPTY) {
            @Override
            List<File> transform(File primaryInput, File outputDir, ArtifactTransformDependenciesProvider dependenciesProvider) {
                instant.b
                thread.blockUntil.a
                instant.b_done
                [new TestFile(outputDir, primaryInput.getName()).touch()]
            }
        }

        when:
        async {
            start {
                transformerInvoker.invoke(new File("a"), transformerRegistrationA, subject)
            }
            start {
                transformerInvoker.invoke(new File("b"), transformerRegistrationB, subject)
            }
        }

        then:
        instant.a_done > instant.b
        instant.b_done > instant.a

        and:
        2 * snapshotter.snapshot(_) >>> [snapshot(HashCode.fromInt(234)), snapshot(HashCode.fromInt(456))]
        2 * fileAccessTimeJournal.setLastAccessTime(_, _)
    }

    def "does not reuse result when transform inputs are different"() {
        def transform1 = Mock(DefaultTransformer)
        def transform2 = Mock(DefaultTransformer)
        def inputFile = tmpDir.file("a")

        given:
        _ * transform1.transform(inputFile, _) >> { File file, File dir -> def r = new File(dir, "a.1"); r.text = "result"; [r] }
        _ * snapshotter.snapshot(inputFile) >> snapshot(HashCode.fromInt(234))
        _ * transform1.secondaryInputHash >> HashCode.fromInt(123)
        transformerInvoker.invoke(inputFile, transform1, subject)

        when:
        def result = transformerInvoker.invoke(inputFile, transform2, subject)

        then:
        result*.name == ["a.2"]

        and:
        1 * transform2.transform(inputFile, _) >>  { File file, File dir -> def r = new File(dir, "a.2"); r.text = "result"; [r] }
        _ * transform2.secondaryInputHash >> HashCode.fromInt(234)
        0 * transform1._
        0 * transform2._
        1 * fileAccessTimeJournal.setLastAccessTime(_, _)

        when:
        def result2 = transformerInvoker.invoke(inputFile, transform1, subject, dependenciesProvider)
        def result3 = transformerInvoker.invoke(inputFile, transform2, subject, dependenciesProvider)

        then:
        result2*.name == ["a.1"]
        result3 == result

        and:
        _ * transform1.secondaryInputHash >> HashCode.fromInt(123)
        _ * transform2.secondaryInputHash >> HashCode.fromInt(234)
        0 * transform1._
        0 * transform2._
        0 * fileAccessTimeJournal._
    }

    def "does not reuse result when transform input files have different content"() {
        def transform1 = Mock(DefaultTransformer)
        def transform2 = Mock(DefaultTransformer)
        def inputFile = tmpDir.file("a")

        given:
        _ * transform1.transform(inputFile, _) >> { File file, File dir -> def r = new File(dir, "a.1"); r.text = "result"; [r] }
        _ * transform1.secondaryInputHash >> HashCode.fromInt(123)
        _ * snapshotter.snapshot(inputFile) >> snapshot(HashCode.fromInt(234))

        transformerInvoker.invoke(inputFile, transform1, subject)

        when:
        def result = transformerInvoker.invoke(inputFile, transform2, subject)

        then:
        result*.name == ["a.2"]

        and:
        1 * transform2.secondaryInputHash >> HashCode.fromInt(345)
        1 * snapshotter.snapshot(inputFile) >> snapshot(HashCode.fromInt(456))
        1 * transform2.transform(inputFile, _) >>  { File file, File dir -> def r = new File(dir, "a.2"); r.text = "result"; [r] }
        0 * transform1._
        0 * transform2._
        1 * fileAccessTimeJournal.setLastAccessTime(_, _)

        when:
        def result2 = transformerInvoker.invoke(inputFile, transform1, subject, dependenciesProvider)
        def result3 = transformerInvoker.invoke(inputFile, transform2, subject, dependenciesProvider)

        then:
        result2*.name == ["a.1"]
        result3 == result

        and:
        _ * transform1.secondaryInputHash >> HashCode.fromInt(123)
        _ * transform2.secondaryInputHash >> HashCode.fromInt(345)
        2 * snapshotter.snapshot(inputFile) >>> [snapshot(HashCode.fromInt(234)), snapshot(HashCode.fromInt(456))]
        0 * fileAccessTimeJournal.setLastAccessTime(_, _)
        0 * transform1._
        0 * transform2._
    }

    def "runs transform when previous execution failed and cleans up directory"() {
        def transform = Mock(DefaultTransformer)
        def failure = new RuntimeException()
        def inputFile = tmpDir.file("a")

        when:
        transformerInvoker.invoke(inputFile, transform, subject)

        then:
        def e = thrown(RuntimeException)
        e.is(failure)

        and:
        1 * transform.secondaryInputHash >> HashCode.fromInt(123)
        _ * transform.implementationClass >> ArtifactTransform
        1 * snapshotter.snapshot(inputFile) >> snapshot(HashCode.fromInt(456))
        1 * transform.transform(inputFile, _) >>  { File file, File dir ->
            dir.mkdirs()
            new File(dir, "delete-me").text = "broken"
            throw failure
        }
        0 * transform._

        when:
        def result = transformerInvoker.invoke(inputFile, transform, subject, dependenciesProvider)

        then:
        result*.name == ["a.1"]

        and:
        1 * transform.secondaryInputHash >> HashCode.fromInt(123)
        1 * snapshotter.snapshot(inputFile) >> snapshot(HashCode.fromInt(456))
        1 * transform.transform(inputFile, _) >>  { File file, File dir ->
            assert dir.list().length == 0
            def r = new File(dir, "a.1")
            r.text = "result"
            [r]
        }
        1 * fileAccessTimeJournal.setLastAccessTime(_, _)
        0 * transform._
    }

    def "runs transform when output has been removed"() {
        def transform = Mock(DefaultTransformer)
        def inputFile = tmpDir.file("a")

        given:
        1 * transform.secondaryInputHash >> HashCode.fromInt(123)
        1 * snapshotter.snapshot(inputFile) >> snapshot(HashCode.fromInt(456))
        1 * transform.transform(inputFile, _) >>  { File file, File dir -> def r = new File(dir, "a.1"); r.text = "result"; [r] }

        def result = transformerInvoker.invoke(inputFile, transform, subject)

        when:
        def cache = createInvoker()
        result.first().delete()
        def result2 = cache.invoke(inputFile, transform, subject, dependenciesProvider)

        then:
        result2 == result

        and:
        1 * transform.secondaryInputHash >> HashCode.fromInt(123)
        1 * snapshotter.snapshot(inputFile) >> snapshot(HashCode.fromInt(456))
        1 * transform.transform(inputFile, _) >>  { File file, File dir -> def r = new File(dir, "a.1"); r.text = "result"; [r] }
        1 * fileAccessTimeJournal.setLastAccessTime(_, _)
        0 * transform._
    }

    def "stopping the cache cleans up old entries and preserves new ones"() {
        given:
        snapshotter.snapshot(_) >> snapshot(HashCode.fromInt(42))
        def filesDir = transformsStoreDirectory.file(CacheLayout.TRANSFORMS_STORE.getKey())
        def file1 = filesDir.file("some.jar", "bac62a0ac6ce00ff016f869e695d5522").createFile("1.txt")
        def file2 = filesDir.file("another.jar", "a89660597ff12d9e0a0397e055e80006").createFile("2.txt")

        when:
        transformerInvoker.stop()

        then:
        1 * fileAccessTimeJournal.getLastAccessTime(file1.parentFile) >> 0
        1 * fileAccessTimeJournal.getLastAccessTime(file2.parentFile) >> System.currentTimeMillis()

        and:
        !file1.exists()
        file2.exists()
    }

    def snapshot(HashCode hashCode) {
        return new RegularFileSnapshot("/path/to/some.txt", "some.txt", hashCode, 0)
    }
}
