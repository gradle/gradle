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

package org.gradle.plugins.binaries.model.internal
import org.gradle.api.internal.tasks.compile.Compiler
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.plugins.binaries.model.BinaryCompileSpec
import org.gradle.plugins.binaries.model.ToolChain
import org.gradle.plugins.binaries.model.ToolChainAdapter
import spock.lang.Specification

class DefaultToolChainRegistryTest extends Specification {
    final DefaultToolChainRegistry registry = new DefaultToolChainRegistry(new DirectInstantiator())

    def "search order defaults to the order that adapters are added"() {
        ToolChainAdapter compiler1 = toolChainAdapter("z")
        ToolChainAdapter compiler2 = toolChainAdapter("b")
        ToolChainAdapter compiler3 = toolChainAdapter("a")

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
        BinaryCompileSpec compileSpec = Mock()
        ToolChainAdapter toolChainAdapter1 = toolChainAdapter("z")
        ToolChainAdapter toolChainAdapter2 = toolChainAdapter("b")
        ToolChainAdapter toolChainAdapter3 = toolChainAdapter("a")
        ToolChain realToolChain = Mock()
        Compiler<BinaryCompileSpec> realCompiler = Mock()

        given:
        registry.add(toolChainAdapter1)
        registry.add(toolChainAdapter2)
        registry.add(toolChainAdapter3)

        and:
        toolChainAdapter2.available >> true

        when:
        def defaultToolChain = registry.getDefaultToolChain()
        defaultToolChain.createCompiler(BinaryCompileSpec).execute(compileSpec)

        then:
        1 * toolChainAdapter2.create() >> realToolChain
        1 * realToolChain.createCompiler(BinaryCompileSpec) >> realCompiler
        1 * realCompiler.execute(compileSpec)
    }

    def "compilation fails when no adapter is available"() {
        BinaryCompileSpec compileSpec = Mock()
        ToolChainAdapter toolChainAdapter1 = toolChainAdapter("z")
        ToolChainAdapter toolChainAdapter2 = toolChainAdapter("b")
        ToolChainAdapter toolChainAdapter3 = toolChainAdapter("a")

        given:
        registry.add(toolChainAdapter1)
        registry.add(toolChainAdapter2)
        registry.add(toolChainAdapter3)

        when:
        def defaultToolChain = registry.getDefaultToolChain()
        defaultToolChain.createCompiler(BinaryCompileSpec).execute(compileSpec)

        then:
        IllegalStateException e = thrown()
        e.message == "No tool chain is available. Searched for $toolChainAdapter1, $toolChainAdapter2, $toolChainAdapter3."
    }

    def toolChainAdapter(String name) {
        ToolChainAdapter toolChainAdapter = Mock()
        _ * toolChainAdapter.name >> name
        return toolChainAdapter
    }
}
