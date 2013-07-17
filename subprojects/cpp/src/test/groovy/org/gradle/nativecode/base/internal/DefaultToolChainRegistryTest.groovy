/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.nativecode.base.internal
import org.gradle.api.internal.tasks.compile.Compiler
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.nativecode.language.cpp.internal.NativeCompileSpec
import spock.lang.Specification

class DefaultToolChainRegistryTest extends Specification {
    final DefaultToolChainRegistry registry = new DefaultToolChainRegistry(new DirectInstantiator())

    def "search order defaults to the order that adapters are added"() {
        ToolChainInternal compiler1 = toolChainInternal("z")
        ToolChainInternal compiler2 = toolChainInternal("b")
        ToolChainInternal compiler3 = toolChainInternal("a")

        expect:
        registry.searchOrder == []

        when:
        registry.add(compiler2)
        registry.add(compiler1)
        registry.add(compiler3)

        then:
        registry.searchOrder == [compiler2, compiler1, compiler3]

        when:
        registry.remove(compiler1)

        then:
        registry.searchOrder == [compiler2, compiler3]
    }

    def "compilation searches adapters in the order added and uses the first available"() {
        NativeCompileSpec compileSpec = Mock()
        ToolChainInternal toolChainInternal1 = toolChainInternal("z")
        ToolChainInternal toolChainInternal2 = toolChainInternal("b")
        ToolChainInternal toolChainInternal3 = toolChainInternal("a")
        Compiler<NativeCompileSpec> realCompiler = Mock()

        given:
        registry.add(toolChainInternal1)
        registry.add(toolChainInternal2)
        registry.add(toolChainInternal3)

        and:
        toolChainInternal1.availability >> new ToolChainAvailability().unavailable("nope")
        toolChainInternal2.availability >> new ToolChainAvailability()

        when:
        def defaultToolChain = registry.getDefaultToolChain()
        defaultToolChain.createCCompiler().execute(compileSpec)

        then:
        1 * toolChainInternal2.createCCompiler() >> realCompiler
        1 * realCompiler.execute(compileSpec)
    }

    def "compilation fails when no adapter is available"() {
        NativeCompileSpec compileSpec = Mock()
        ToolChainInternal toolChainInternal1 = toolChainInternal("z")
        ToolChainInternal toolChainInternal2 = toolChainInternal("b")
        ToolChainInternal toolChainInternal3 = toolChainInternal("a")

        given:
        registry.add(toolChainInternal1)
        registry.add(toolChainInternal2)
        registry.add(toolChainInternal3)

        and:
        toolChainInternal1.availability >> new ToolChainAvailability().unavailable("nope")
        toolChainInternal2.availability >> new ToolChainAvailability().unavailable("not me")
        toolChainInternal3.availability >> new ToolChainAvailability().unavailable("not me either")

        when:
        def defaultToolChain = registry.getDefaultToolChain()

        then:
        noExceptionThrown()

        when:
        defaultToolChain.createCppCompiler().execute(compileSpec)

        then:
        IllegalStateException e = thrown()
        e.message == "No tool chain is available: [Could not load 'z': nope, Could not load 'b': not me, Could not load 'a': not me either]"
    }

    def toolChainInternal(String name) {
        ToolChainInternal toolChainInternal = Mock()
        _ * toolChainInternal.name >> name
        return toolChainInternal
    }
}
