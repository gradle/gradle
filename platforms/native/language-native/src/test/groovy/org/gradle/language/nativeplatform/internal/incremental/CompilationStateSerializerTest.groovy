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

import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import org.gradle.internal.hash.HashCode
import org.gradle.internal.hash.TestHashCodes
import org.gradle.internal.serialize.SerializerSpec

class CompilationStateSerializerTest extends SerializerSpec {
    private CompilationStateSerializer serializer = new CompilationStateSerializer()

    def "serializes empty state"() {
        def state = new CompilationState()

        expect:
        with (serialized(state)) {
            sourceInputs.empty
            fileStates.isEmpty()
        }
    }

    def "serializes state with source files"() {
        when:
        def fileEmpty = new File("empty")
        def fileStates = [:]
        fileStates.put(fileEmpty, compilationFileState(TestHashCodes.hashCodeFrom(0x12345678), []))

        def fileTwo = new File("two")
        def stateTwo = compilationFileState(TestHashCodes.hashCodeFrom(0x23456789), ["ONE", "TWO"])
        fileStates.put(fileTwo, stateTwo)
        def state = compilationState(fileStates)

        then:
        def newState = serialized(state)
        newState.fileStates.size() == 2

        def emptyCompileState = newState.getState(fileEmpty)
        emptyCompileState.hash == TestHashCodes.hashCodeFrom(0x12345678)
        emptyCompileState.edges.empty

        def otherCompileState = newState.getState(fileTwo)
        otherCompileState.hash == TestHashCodes.hashCodeFrom(0x23456789)
        otherCompileState.edges == stateTwo.edges
    }

    def "serializes state with shared include files"() {
        when:
        def fileOne = new File("one")
        def fileStates = [:]
        def stateOne = compilationFileState(TestHashCodes.hashCodeFrom(0x12345678), ["ONE", "TWO"])
        fileStates.put(fileOne, stateOne)

        def fileTwo = new File("two")
        def stateTwo = compilationFileState(TestHashCodes.hashCodeFrom(0x23456789), ["TWO", "THREE"])
        fileStates.put(fileTwo, stateTwo)
        def state = compilationState(fileStates)

        then:
        def newState = serialized(state)
        newState.fileStates.size() == 2

        def emptyCompileState = newState.getState(fileOne)
        emptyCompileState.hash == TestHashCodes.hashCodeFrom(0x12345678)
        emptyCompileState.edges == stateOne.edges

        def otherCompileState = newState.getState(fileTwo)
        otherCompileState.hash == TestHashCodes.hashCodeFrom(0x23456789)
        otherCompileState.edges == stateTwo.edges
    }

    private SourceFileState compilationFileState(HashCode hash, Collection<String> includes) {
        return new SourceFileState(hash, true, ImmutableSet.copyOf(includes.collect { new IncludeFileEdge(it, null, TestHashCodes.hashCodeFrom(123) )}))
    }

    private CompilationState compilationState(Map<File, SourceFileState> states) {
        return new CompilationState(ImmutableMap.copyOf(states))
    }

    private CompilationState serialized(CompilationState state) {
        serialize(state, serializer) as CompilationState
    }
}
