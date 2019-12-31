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

import com.google.common.collect.ImmutableList
import org.gradle.api.Action
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact
import org.gradle.api.internal.tasks.NodeExecutionContext
import org.gradle.internal.Try
import org.gradle.internal.operations.BuildOperationQueue
import org.gradle.internal.operations.RunnableBuildOperation
import spock.lang.Specification

class ChainedTransformerTest extends Specification {

    def "applies second transform on the result of the first"() {
        given:
        def initialSubject = subject()
        def chain = new TransformationChain(new TestTransformation("first"), new TestTransformation("second"))
        def receiver = Mock(Transformation.ResultReceiver)

        when:
        chain.startTransformation(initialSubject, Mock(ExecutionGraphDependenciesResolver), null, true, Stub(BuildOperationQueue), receiver)

        then:
        1 * receiver.completed(initialSubject, { it.get().files == [new File("foo/first/second")] })
    }

    private TransformationSubject subject() {
        def artifact = Stub(ResolvableArtifact)
        _ * artifact.file >> new File("foo")
        TransformationSubject.initial(artifact)
    }

    class TestTransformation implements Transformation {

        private final String name

        TestTransformation(String name) {
            this.name = name
        }

        @Override
        void startTransformation(TransformationSubject subjectToTransform, ExecutionGraphDependenciesResolver dependenciesResolver, NodeExecutionContext context, boolean isTopLevel, BuildOperationQueue<RunnableBuildOperation> workQueue, ResultReceiver resultReceiver) {
            resultReceiver.completed(subjectToTransform, Try.successful(
                subjectToTransform.createSubjectFromResult(ImmutableList.of(new File(subjectToTransform.files.first(), name)))
            ))
        }

        @Override
        boolean requiresDependencies() {
            return false
        }

        @Override
        void visitTransformationSteps(Action<? super TransformationStep> action) {
        }


        @Override
        boolean endsWith(Transformation otherTransform) {
            return false
        }

        @Override
        int stepsCount() {
            return 1
        }

        @Override
        String getDisplayName() {
            return name
        }
    }
}
