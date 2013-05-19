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
import spock.lang.Specification

class DefaultCompilerRegistryTest extends Specification {
    final DefaultCompilerRegistry registry = new DefaultCompilerRegistry(new DirectInstantiator())

    def "search order defaults to the order that adapters are added"() {
        CompilerAdapter<BinaryCompileSpec> compiler1 = compilerAdapter("z")
        CompilerAdapter<BinaryCompileSpec> compiler2 = compilerAdapter("b")
        CompilerAdapter<BinaryCompileSpec> compiler3 = compilerAdapter("a")

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
        CompilerAdapter<BinaryCompileSpec> compiler1 = compilerAdapter("z")
        CompilerAdapter<BinaryCompileSpec> compiler2 = compilerAdapter("b")
        CompilerAdapter<BinaryCompileSpec> compiler3 = compilerAdapter("a")
        Compiler<BinaryCompileSpec> realCompiler2 = Mock()

        given:
        registry.add(compiler1)
        registry.add(compiler2)
        registry.add(compiler3)

        and:
        compiler2.available >> true

        when:
        def defaultCompiler = registry.getDefaultCompiler()
        defaultCompiler.execute(compileSpec)

        then:
        1 * compiler2.createCompiler() >> realCompiler2
        1 * realCompiler2.execute(compileSpec)
    }

    def "compilation fails when no adapter is available"() {
        BinaryCompileSpec compileSpec = Mock()
        CompilerAdapter<BinaryCompileSpec> compiler1 = compilerAdapter("z")
        CompilerAdapter<BinaryCompileSpec> compiler2 = compilerAdapter("b")
        CompilerAdapter<BinaryCompileSpec> compiler3 = compilerAdapter("a")

        given:
        registry.add(compiler1)
        registry.add(compiler2)
        registry.add(compiler3)

        when:
        def defaultCompiler = registry.getDefaultCompiler()
        defaultCompiler.execute(compileSpec)

        then:
        IllegalStateException e = thrown()
        e.message == "No compiler is available. Searched for $compiler1, $compiler2, $compiler3."
    }

    def compilerAdapter(String name) {
        CompilerAdapter<BinaryCompileSpec> compiler = Mock()
        _ * compiler.name >> name
        return compiler
    }
}
