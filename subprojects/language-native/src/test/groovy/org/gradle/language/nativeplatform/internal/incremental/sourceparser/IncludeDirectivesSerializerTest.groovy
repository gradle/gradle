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

package org.gradle.language.nativeplatform.internal.incremental.sourceparser

import com.google.common.collect.ImmutableList
import org.gradle.internal.serialize.SerializerSpec
import org.gradle.language.nativeplatform.internal.IncludeType

class IncludeDirectivesSerializerTest extends SerializerSpec {
    def "serializes empty directives"() {
        def directives = new DefaultIncludeDirectives(ImmutableList.of(), ImmutableList.of(), ImmutableList.of())

        expect:
        serialize(directives, new IncludeDirectivesSerializer()) == directives
    }

    def "serializes include directives"() {
        def include1 = new DefaultInclude("one.h", true, IncludeType.QUOTED)
        def include2 = new DefaultInclude("two.h", true, IncludeType.SYSTEM)
        def include3 = new DefaultInclude("three.h", false, IncludeType.MACRO)
        def include4 = new MacroFunctionInclude("A", true, ImmutableList.of(new DefaultExpression("X", IncludeType.MACRO), new DefaultExpression("Y", IncludeType.MACRO)))
        def directives = new DefaultIncludeDirectives(ImmutableList.copyOf([include1, include2, include3, include4]), ImmutableList.of(), ImmutableList.of())

        expect:
        serialize(directives, new IncludeDirectivesSerializer()) == directives
    }

    def "serializes macro directives"() {
        def macro1 = new DefaultMacro("ONE", IncludeType.QUOTED,"one")
        def macro2 = new DefaultMacro("TWO", IncludeType.MACRO, "two")
        def macro3 = new UnresolveableMacro("THREE")
        def macro4 = new UnresolveableMacro("FOUR")
        def directives = new DefaultIncludeDirectives(ImmutableList.of(), ImmutableList.copyOf([macro1, macro2, macro3, macro4]), ImmutableList.of())

        expect:
        serialize(directives, new IncludeDirectivesSerializer()) == directives
    }

    def "serializes macro function directives"() {
        def macro1 = new DefaultMacroFunction("ONE", 0, IncludeType.QUOTED,"one")
        def macro2 = new DefaultMacroFunction("TWO", 3, IncludeType.MACRO, "two")
        def macro3 = new UnresolveableMacroFunction("THREE", 0)
        def macro4 = new UnresolveableMacroFunction("FOUR", 3)
        def directives = new DefaultIncludeDirectives(ImmutableList.of(), ImmutableList.of(), ImmutableList.copyOf([macro1, macro2, macro3, macro4]))

        expect:
        serialize(directives, new IncludeDirectivesSerializer()) == directives
    }
}
