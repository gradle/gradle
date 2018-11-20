/*
 * Copyright 2018 the original author or authors.
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
import org.gradle.api.artifacts.transform.TransformationException
import org.gradle.api.internal.cache.StringInterner
import org.gradle.api.internal.file.TestFiles
import org.gradle.internal.classloader.ClassLoaderHierarchyHasher
import org.gradle.internal.execution.WorkExecutor
import org.gradle.internal.execution.impl.steps.UpToDateResult
import org.gradle.internal.fingerprint.impl.OutputFileCollectionFingerprinter
import org.gradle.internal.hash.HashCode
import org.gradle.internal.hash.TestFileHasher
import org.gradle.internal.snapshot.WellKnownFileLocations
import org.gradle.internal.snapshot.impl.DefaultFileSystemMirror
import org.gradle.internal.snapshot.impl.DefaultFileSystemSnapshotter
import spock.lang.Specification

class TransformerInvokerTest extends Specification {

    def transformer = Mock(Transformer)
    def sourceSubject = Mock(TransformationSubject)
    def sourceFile = new File("source")
    WorkExecutor<UpToDateResult> workExecutor = Mock()
    def fileHasher = new TestFileHasher()
    def fileSystemMirror = new DefaultFileSystemMirror(Stub(WellKnownFileLocations))
    def snapshotter = new DefaultFileSystemSnapshotter(fileHasher, new StringInterner(), TestFiles.fileSystem(), fileSystemMirror)
    def artifactTransformListener = Mock(ArtifactTransformListener)
    def historyRepository = Mock(TransformerExecutionHistoryRepository)
    def outputFileCollectionFingerprinter = Mock(OutputFileCollectionFingerprinter)
    def transformerInvoker = new DefaultTransformerInvoker(workExecutor, snapshotter, artifactTransformListener, historyRepository, outputFileCollectionFingerprinter, Mock(ClassLoaderHierarchyHasher))

    def "wraps failures into TransformInvocationException"() {
        def failure = new RuntimeException()
        def executionResult = Mock(UpToDateResult)

        when:
        def result = transformerInvoker.invoke(transformer, sourceFile, sourceSubject, Mock(ArtifactTransformDependenciesProvider))
        def transformationFailure = result.failure.get()
        then:
        transformationFailure instanceof TransformationException
        transformationFailure.message == "Failed to transform file 'source' using transform ArtifactTransform"
        transformationFailure.cause.is(failure)

        and:
        1 * workExecutor.execute(_) >> executionResult
        _ * executionResult.failure >> failure
        1 * transformer.implementationClass >> ArtifactTransform
        _ * transformer.getSecondaryInputHash() >> HashCode.fromInt(1234)
        1 * historyRepository.withWorkspace(_, _) >> { TransformationIdentity identity, action ->
            action.apply(identity.getIdentity(), new File("workspace"))
        }
        _ * artifactTransformListener._
        0 * _
    }
}
