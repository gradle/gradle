/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.language.nativeplatform.internal.incremental

import org.gradle.internal.serialize.SerializerSpec
import org.gradle.language.nativeplatform.internal.incremental.sourceparser.DefaultInclude
import org.gradle.language.nativeplatform.internal.incremental.sourceparser.DefaultSourceIncludes

class CompilationStateSerializerTest extends SerializerSpec {
    def state = new CompilationState()
    private CompilationStateSerializer serializer = new CompilationStateSerializer()

    def "serializes empty state"() {
        expect:
        with (serialized) {
            sourceInputs.empty
            fileStates.isEmpty()
        }
    }

    def "serializes source inputs"() {
        when:
        def fileOne = new File("one")
        def fileTwo = new File("two")
        state.sourceInputs << fileOne << fileTwo

        then:
        with (serialized) {
            sourceInputs == [fileOne, fileTwo]
            fileStates.isEmpty()
        }
    }

    def "serializes file state"() {
        when:
        def fileEmpty = new File("empty")
        state.fileStates.put(fileEmpty, new CompilationFileState(new byte[0]))

        def fileTwo = new File("two")
        def stateTwo = new CompilationFileState("FooBar".getBytes())
        stateTwo.sourceIncludes = createSourceIncludes("<system>", '"quoted"', "MACRO")
        stateTwo.resolvedIncludes = [resolvedInclude("ONE"), resolvedInclude("TWO")]
        state.fileStates.put(fileTwo, stateTwo)

        then:
        def newState = serialized
        newState.sourceInputs.empty
        newState.fileStates.size() == 2

        def emptyCompileState = newState.getState(fileEmpty)
        emptyCompileState.hash.length == 0
        emptyCompileState.sourceIncludes.macroIncludes.empty
        emptyCompileState.sourceIncludes.quotedIncludes.empty
        emptyCompileState.sourceIncludes.systemIncludes.empty
        emptyCompileState.resolvedIncludes.empty

        def otherCompileState = newState.getState(fileTwo)
        new String(otherCompileState.hash) == "FooBar"
        otherCompileState.sourceIncludes.systemIncludes.collect { it.value } == ["system"]
        otherCompileState.sourceIncludes.quotedIncludes.collect { it.value } == ["quoted"]
        otherCompileState.sourceIncludes.macroIncludes.collect { it.value } == ["MACRO"]
        otherCompileState.resolvedIncludes == [resolvedInclude("ONE"), resolvedInclude("TWO")] as Set
    }

    private static DefaultSourceIncludes createSourceIncludes(String... strings) {
        final DefaultSourceIncludes sourceIncludes = new DefaultSourceIncludes()
        sourceIncludes.addAll(strings.collect { DefaultInclude.parse(it, false) })
        sourceIncludes
    }

    private static ResolvedInclude resolvedInclude(String value) {
        return new ResolvedInclude(value, new File(value))
    }

    private CompilationState getSerialized() {
        serialize(state, serializer) as CompilationState
    }
}
