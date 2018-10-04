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

class ArtifactTransformationStepTest extends Specification {

    def transformer = Mock(TransformerRegistration)
    def transformedFileCache = Mock(TransformedFileCache)
    def transformationListener = Mock(ArtifactTransformListener)
    def sourceSubject = Mock(TransformationSubject)
    def transformationStep = new ArtifactTransformationStep(transformer, transformedFileCache, transformationListener)

    def "calls the transformation listener on each transformed file"() {
        def fileOne = new File("one")
        def resultOne = new File("one")
        def fileTwo = new File("two")
        def resultTwo = new File("two")
        def resultThree = new File("two")
        def transformerInputsHash = HashCode.fromInt(1234)

        when:
        def transformedSubject = transformationStep.transform(sourceSubject)
        then:
        transformedSubject.files == [resultOne, resultTwo, resultThree]

        1 * sourceSubject.getFailure() >> null
        1 * transformer.getInputsHash() >> transformerInputsHash
        1 * sourceSubject.getFiles() >> [fileOne, fileTwo]
        1 * transformedFileCache.contains(fileOne.getAbsoluteFile(), transformerInputsHash) >> false
        1 * transformationListener.beforeTransform(transformer, sourceSubject)

        then:
        1 * transformedFileCache.runTransformer(fileOne, transformer) >> [resultOne]

        then:
        2 * transformationListener.afterTransform(transformer, sourceSubject)
        1 * transformationListener.beforeTransform(transformer, sourceSubject)
        1 * transformer.getInputsHash() >> transformerInputsHash
        1 * transformedFileCache.contains(fileTwo.getAbsoluteFile(), transformerInputsHash) >> false
        1 * transformedFileCache.runTransformer(fileTwo, transformer) >> [resultTwo, resultThree]
        0 * _
    }
    
    def "calls the transformation listener if a transformer fails"() {
        def transformerInputsHash = HashCode.fromInt(1234)
        def sourceFile = new File("source")
        def failure = new RuntimeException()

        when:
        def transformedSubject = transformationStep.transform(sourceSubject)
        then:
        transformedSubject.failure == failure

        1 * sourceSubject.getFailure() >> null
        1 * transformer.getInputsHash() >> transformerInputsHash
        1 * sourceSubject.getFiles() >> [sourceFile]
        1 * transformedFileCache.contains(sourceFile.getAbsoluteFile(), transformerInputsHash) >> false
        1 * transformationListener.beforeTransform(transformer, sourceSubject)

        then:
        1 * transformedFileCache.runTransformer(sourceFile, transformer) >> { throw failure }

        then:
        1 * transformationListener.afterTransform(transformer, sourceSubject)
        0 * _
    }
}
