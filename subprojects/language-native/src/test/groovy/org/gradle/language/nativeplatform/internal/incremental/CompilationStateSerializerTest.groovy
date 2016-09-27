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
import com.google.common.hash.HashCode
import org.gradle.internal.serialize.SerializerSpec
import org.gradle.language.nativeplatform.internal.IncludeDirectives
import org.gradle.language.nativeplatform.internal.incremental.sourceparser.DefaultInclude
import org.gradle.language.nativeplatform.internal.incremental.sourceparser.DefaultIncludeDirectives

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

    def "serializes source inputs"() {
        when:
        def fileOne = new File("one")
        def fileTwo = new File("two")
        def state = compilationState([fileOne, fileTwo], [:])

        then:
        with (serialized(state)) {
            sourceInputs == [fileOne, fileTwo] as Set
            fileStates.isEmpty()
        }
    }

    def "serializes file state"() {
        when:
        def fileEmpty = new File("empty")
        def fileStates = [:]
        fileStates.put(fileEmpty, compilationFileState(HashCode.fromString("1234"), createSourceIncludes(), []))

        def fileTwo = new File("two")
        def stateTwo = compilationFileState(HashCode.fromString("2345"), createSourceIncludes("<system>", '"quoted"', "MACRO"), [resolvedInclude("ONE"), resolvedInclude("TWO")])
        fileStates.put(fileTwo, stateTwo)
        def state = compilationState([], fileStates)

        then:
        def newState = serialized(state)
        newState.sourceInputs.empty
        newState.fileStates.size() == 2

        def emptyCompileState = newState.getState(fileEmpty)
        emptyCompileState.hash == HashCode.fromString("1234")
        emptyCompileState.includeDirectives.macroIncludes.empty
        emptyCompileState.includeDirectives.quotedIncludes.empty
        emptyCompileState.includeDirectives.systemIncludes.empty
        emptyCompileState.resolvedIncludes.empty

        def otherCompileState = newState.getState(fileTwo)
        otherCompileState.hash == HashCode.fromString("2345")
        otherCompileState.includeDirectives.systemIncludes.collect { it.value } == ["system"]
        otherCompileState.includeDirectives.quotedIncludes.collect { it.value } == ["quoted"]
        otherCompileState.includeDirectives.macroIncludes.collect { it.value } == ["MACRO"]
        otherCompileState.resolvedIncludes == [resolvedInclude("ONE"), resolvedInclude("TWO")] as Set
    }

    private DefaultIncludeDirectives createSourceIncludes(String... strings) {
        return new DefaultIncludeDirectives(strings.collect { DefaultInclude.parse(it, false) })
    }

    private CompilationFileState compilationFileState(HashCode hash, IncludeDirectives includeDirectives, Collection<ResolvedInclude> resolvedIncludes) {
        return new CompilationFileState(hash, includeDirectives, ImmutableSet.copyOf(resolvedIncludes))
    }

    private CompilationState compilationState(Collection<File> sourceFiles, Map<File, CompilationFileState> states) {
        return new CompilationState(ImmutableSet.copyOf(sourceFiles), ImmutableMap.copyOf(states))
    }

    private ResolvedInclude resolvedInclude(String value) {
        return new ResolvedInclude(value, new File(value))
    }

    private CompilationState serialized(CompilationState state) {
        serialize(state, serializer) as CompilationState
    }
}
