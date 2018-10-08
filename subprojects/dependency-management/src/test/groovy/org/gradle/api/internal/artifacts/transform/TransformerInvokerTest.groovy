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

import org.gradle.internal.hash.HashCode
import spock.lang.Specification

class TransformerInvokerTest extends Specification {

    def transformer = Mock(Transformer)
    def transformedFileCache = Mock(TransformedFileCache)
    def transformationListener = Mock(ArtifactTransformListener)
    def sourceSubject = Mock(TransformationSubject)
    def transformerInvoker = new DefaultTransformerInvoker(transformedFileCache, transformationListener)

    def "calls the transformation listener on each transformed file"() {
        def fileOne = new File("one")
        def resultOne = new File("one")
        def resultTwo = new File("two")
        def transformerInputsHash = HashCode.fromInt(1234)
        def invocation = createInvocation(fileOne)

        when:
        transformerInvoker.invoke(invocation)
        then:
        invocation.result == [resultOne, resultTwo]

        1 * transformer.getInputsHash() >> transformerInputsHash
        1 * transformedFileCache.contains(fileOne.getAbsoluteFile(), transformerInputsHash) >> false
        1 * transformationListener.beforeTransform(transformer, sourceSubject)

        then:
        1 * transformedFileCache.runTransformer(fileOne, transformer) >> [resultOne, resultTwo]

        then:
        1 * transformationListener.afterTransform(transformer, sourceSubject)
        0 * _
    }

    private TransformerInvocation createInvocation(File fileOne) {
        new TransformerInvocation(transformer, fileOne, sourceSubject)
    }

    def "calls the transformation listener if a transformer fails"() {
        def transformerInputsHash = HashCode.fromInt(1234)
        def sourceFile = new File("source")
        def failure = new RuntimeException()

        def invocation = createInvocation(sourceFile)
        when:
        transformerInvoker.invoke(invocation)
        then:
        invocation.failure == failure

        1 * transformer.getInputsHash() >> transformerInputsHash
        1 * transformedFileCache.contains(sourceFile.getAbsoluteFile(), transformerInputsHash) >> false
        1 * transformationListener.beforeTransform(transformer, sourceSubject)

        then:
        1 * transformedFileCache.runTransformer(sourceFile, transformer) >> { throw failure }

        then:
        1 * transformationListener.afterTransform(transformer, sourceSubject)
        0 * _
    }
}
