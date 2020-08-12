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
import org.gradle.api.internal.tasks.NodeExecutionContext
import org.gradle.internal.Try
import spock.lang.Specification

class ChainedTransformerTest extends Specification {
    private TransformationSubject initialSubject = TransformationSubject.initial(new File("foo"))

    def "applies second transform on the result of the first"() {
        given:
        def chain = new TransformationChain(new TestTransformation("first"), new TestTransformation("second"))

        expect:
        chain.createInvocation(initialSubject, Mock(ExecutionGraphDependenciesResolver), null).invoke().get().files == [new File("foo/first/second")]
    }

    class TestTransformation implements Transformation {

        private final String name

        TestTransformation(String name) {
            this.name = name
        }

        @Override
        CacheableInvocation<TransformationSubject> createInvocation(TransformationSubject subjectToTransform, ExecutionGraphDependenciesResolver dependenciesResolver, NodeExecutionContext context) {
            return CacheableInvocation.cached(Try.successful(
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
        void isolateParameters() {
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
