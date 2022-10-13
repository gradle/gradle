/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.integtests.resolve.transform

import com.google.common.collect.ImmutableList
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.attributes.Attribute
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.internal.artifacts.VariantTransformRegistry
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact
import org.gradle.api.internal.artifacts.transform.DefaultTransformUpstreamDependenciesResolver
import org.gradle.api.internal.artifacts.transform.TransformationStep
import org.gradle.api.internal.artifacts.transform.TransformationSubject
import org.gradle.api.internal.artifacts.transform.TransformerInvocationFactory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import org.gradle.internal.Try
import org.gradle.internal.component.local.model.OpaqueComponentArtifactIdentifier
import org.gradle.internal.execution.InputFingerprinter
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.test.fixtures.file.TestFile

import java.util.concurrent.atomic.AtomicInteger

class ArtifactTransformInvocationTest extends AbstractProjectBuilderSpec {
    public static final AtomicInteger INVOCATION_COUNT = new AtomicInteger()
    public static final String SELECTED_PATH = "selected.txt"

    def artifactType = Attribute.of('artifactType', String)

    def setup() {
        INVOCATION_COUNT.set(0)
    }

    def "input artifact selection is restored when using the in-memory cache"() {
        def transform = registerTransform(IdentityTransform)

        def inputArtifact1 = file("input1/input.txt")
        inputArtifact1.text = "Hello"

        def inputArtifact2 = file("input2/input.txt")
        inputArtifact2.text = "Hello"

        expect:
        invokeTransform(transform, inputArtifact1).get() == [inputArtifact1]
        invokeTransform(transform, inputArtifact2).get() == [inputArtifact2]
        INVOCATION_COUNT.get() == 1
    }

    def "input artifact paths are restored when using the in-memory cache"() {
        def transform = registerTransform(SelectFileTransform)

        def inputArtifact1 = file("input1/input.txt")

        def selectedFile1 = inputArtifact1.file(SELECTED_PATH)
        selectedFile1.text = "Hello"

        def inputArtifact2 = file("input2/input.txt")

        def selectedFile2 = inputArtifact2.file(SELECTED_PATH)
        selectedFile2.text = "Hello"

        expect:
        invokeTransform(transform, inputArtifact1).get() == [selectedFile1]
        invokeTransform(transform, inputArtifact2).get() == [selectedFile2]
    }

    def "the order is retained when mixing input artifacts and produced artifacts"() {
        def transform = registerTransform(MixedTransform)

        def inputArtifact1 = file("input1/input.txt")

        def selectedFile1 = inputArtifact1.file(SELECTED_PATH)
        selectedFile1.text = "Hello"

        def inputArtifact2 = file("input2/input.txt")

        def selectedFile2 = inputArtifact2.file(SELECTED_PATH)
        selectedFile2.text = "Hello"

        when:
        def transformationResult1 = invokeTransform(transform, inputArtifact1).get()
        def transformationResult2 = invokeTransform(transform, inputArtifact2).get()
        then:
        transformationResult1.size() == 4
        transformationResult2.size() == 4
        transformationResult1[0, 2] == [selectedFile1, inputArtifact1]
        transformationResult2[0, 2] == [selectedFile2, inputArtifact2]
        transformationResult1[1, 3] == transformationResult2[1, 3]
        INVOCATION_COUNT.get() == 1
    }

    private TestFile file(String path) {
        new TestFile(project.file(path))
    }

    private <T extends TransformParameters> TransformationStep registerTransform(Class<? extends TransformAction<T>> actionType) {
        def variantTransformRegistry = project.services.get(VariantTransformRegistry)
        int currentRegisteredTransforms = variantTransformRegistry.transforms.size()
        project.dependencies.registerTransform(actionType) {
            it.from.attribute(artifactType, 'jar')
            it.to.attribute(artifactType, 'transformed')
        }
        return variantTransformRegistry.transforms[currentRegisteredTransforms].transformationStep
    }

    private Try<ImmutableList<File>> invokeTransform(TransformationStep transform, File inputArtifact) {
        transform.isolateParametersIfNotAlready()
        def invocationFactory = project.services.get(TransformerInvocationFactory)
        def inputFingerprinter = project.services.get(InputFingerprinter)
        def artifact = Stub(ResolvableArtifact) {
            getId() >> new OpaqueComponentArtifactIdentifier(inputArtifact)
        }
        def invocation = invocationFactory.createInvocation(
            transform.getTransformer(),
            inputArtifact,
            DefaultTransformUpstreamDependenciesResolver.NO_RESULT,
            TransformationSubject.initial(artifact),
            inputFingerprinter
        )
        invocation.completeAndGet()
    }

    static abstract class IdentityTransform implements TransformAction<TransformParameters.None> {
        @Classpath
        @InputArtifact
        abstract Provider<FileSystemLocation> getInputArtifact()

        void transform(TransformOutputs outputs) {
            INVOCATION_COUNT.incrementAndGet()
            outputs.file(inputArtifact)
        }
    }

    static abstract class SelectFileTransform implements TransformAction<TransformParameters.None> {
        @Classpath
        @InputArtifact
        abstract Provider<FileSystemLocation> getInputArtifact()

        void transform(TransformOutputs outputs) {
            INVOCATION_COUNT.incrementAndGet()
            outputs.file(new File(inputArtifact.get().asFile, SELECTED_PATH))
        }
    }

    static abstract class MixedTransform implements TransformAction<TransformParameters.None> {
        @Classpath
        @InputArtifact
        abstract Provider<FileSystemLocation> getInputArtifact()

        void transform(TransformOutputs outputs) {
            INVOCATION_COUNT.incrementAndGet()
            outputs.file(new File(inputArtifact.get().asFile, SELECTED_PATH))
            outputs.file("produced-file-1.txt").text = "produced1"
            outputs.dir(inputArtifact)
            outputs.file("produced-file-2.txt").text = "produced2"
        }
    }
}
