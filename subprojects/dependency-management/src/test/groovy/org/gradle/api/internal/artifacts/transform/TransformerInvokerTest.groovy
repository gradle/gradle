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
import org.gradle.api.artifacts.transform.TransformInvocationException
import org.gradle.internal.hash.HashCode
import spock.lang.Specification

class TransformerInvokerTest extends Specification {

    def transformer = Mock(Transformer)
    def transformedFileCache = Mock(CachingTransformerExecutor)
    def transformationListener = Mock(ArtifactTransformListener)
    def sourceSubject = Mock(TransformationSubject)
    def transformerInvoker = new DefaultTransformerInvoker(transformedFileCache, transformationListener)
    def transformerInputsHash = HashCode.fromInt(1234)
    def sourceFile = new File("source")

    def "calls the transformation listener on each transformed file"() {
        def resultOne = new File("one")
        def resultTwo = new File("two")
        def invocation = createInvocation(sourceFile)

        when:
        transformerInvoker.invoke(invocation)
        then:
        invocation.result == [resultOne, resultTwo]

        1 * transformer.getSecondaryInputHash() >> transformerInputsHash
        1 * transformedFileCache.contains(sourceFile.getAbsoluteFile(), transformerInputsHash) >> false
        1 * transformationListener.beforeTransformerInvocation(transformer, sourceSubject)

        then:
        1 * transformedFileCache.getResult(sourceFile, transformer) >> [resultOne, resultTwo]

        then:
        1 * transformationListener.afterTransformerInvocation(transformer, sourceSubject)
        0 * _
    }

    def "calls the transformation listener if a transformer fails"() {
        def failure = new RuntimeException()
        def invocation = createInvocation(sourceFile)
        
        when:
        transformerInvoker.invoke(invocation)
        then:
        invocation.failure.cause.is(failure)

        1 * transformer.getSecondaryInputHash() >> transformerInputsHash
        1 * transformedFileCache.contains(sourceFile.getAbsoluteFile(), transformerInputsHash) >> false
        1 * transformationListener.beforeTransformerInvocation(transformer, sourceSubject)

        then:
        1 * transformedFileCache.getResult(sourceFile, transformer) >> { throw failure }

        then:
        1 * transformationListener.afterTransformerInvocation(transformer, sourceSubject)
        1 * transformer.implementationClass >> ArtifactTransform
        0 * _
    }

    def "wraps failures into TransformInvocationException"() {
        def failure = new RuntimeException()
        def invocation = createInvocation(sourceFile)

        when:
        transformerInvoker.invoke(invocation)
        then:
        invocation.failure instanceof TransformInvocationException
        invocation.failure.message == "Failed to transform file 'source' using transform ArtifactTransform"
        invocation.failure.cause.is(failure)

        and:
        1 * transformer.getSecondaryInputHash() >> transformerInputsHash
        1 * transformedFileCache.contains(sourceFile.getAbsoluteFile(), transformerInputsHash) >> false
        1 * transformationListener.beforeTransformerInvocation(transformer, sourceSubject)
        1 * transformedFileCache.getResult(sourceFile, transformer) >> { throw failure }
        1 * transformationListener.afterTransformerInvocation(transformer, sourceSubject)
        1 * transformer.implementationClass >> ArtifactTransform
        0 * _
    }

    private TransformerInvocation createInvocation(File fileOne) {
        new TransformerInvocation(transformer, fileOne, sourceSubject)
    }

}
