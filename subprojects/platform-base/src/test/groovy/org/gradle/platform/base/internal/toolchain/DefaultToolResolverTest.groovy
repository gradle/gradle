/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.platform.base.internal.toolchain

import org.gradle.language.base.internal.compile.CompileSpec
import org.gradle.language.base.internal.compile.Compiler
import org.gradle.platform.base.Platform
import org.gradle.util.TreeVisitor
import spock.lang.Specification

class DefaultToolResolverTest extends Specification {
    def "can find an available toolchain" () {
        def toolChains = [
                Mock(ToolChainInternal) {
                    select(_) >> Mock(ToolProvider) {
                        isAvailable() >> response1
                    }
                },
                Mock(ToolChainInternal) {
                    select(_) >> Mock(ToolProvider) {
                        isAvailable() >> response2
                    }
                }
        ]
        DefaultToolResolver resolver = new DefaultToolResolver()
        toolChains.each { resolver.registerToolChain(it) }

        expect:
        resolver.checkToolAvailability(Stub(Platform)).available == availability

        where:
        response1 | response2 | availability
        false     | true      | true
        true      | false     | true
        false     | false     | false
    }

    def "no available toolchains explains all ToolSearchResults" () {
        def provider1 = Mock(ToolProvider) {
            isAvailable() >> false
        }
        def toolChain1 = Mock(ToolChainInternal) {
            select(_) >> provider1
        }
        def provider2 = Mock(ToolProvider) {
            isAvailable() >> false
        }
        def toolChain2 = Mock(ToolChainInternal) {
            select(_) >> provider2
        }

        DefaultToolResolver resolver = new DefaultToolResolver()
        resolver.registerToolChain(toolChain1)
        resolver.registerToolChain(toolChain2)
        ToolSearchResult result = resolver.checkToolAvailability(Stub(Platform))
        TreeVisitor visitor = Stub(TreeVisitor)

        when:
        result.explain(visitor)

        then:
        provider1.explain(visitor)
        provider2.explain(visitor)
    }

    def "can resolve an available compiler" () {
        def compileSpec = new TestCompileSpec() {}
        def provider = Mock(ToolProvider) {
            isAvailable() >> true
        }
        def toolChain = Mock(ToolChainInternal) {
            select(_) >> provider
        }
        DefaultToolResolver resolver = new DefaultToolResolver()
        resolver.registerToolChain(toolChain)

        when:
        ResolvedTool<Compiler<TestCompileSpec>> tool = resolver.resolveCompiler(TestCompileSpec.class, Stub(Platform))

        then:
        tool.available

        when:
        tool.get()

        then:
        1 * provider.newCompiler(TestCompileSpec.class)
    }

    def "can resolve an available tool" () {
        def provider = Mock(ToolProvider) {
            isAvailable() >> true
        }
        def toolChain = Mock(ToolChainInternal) {
            select(_) >> provider
        }
        DefaultToolResolver resolver = new DefaultToolResolver()
        resolver.registerToolChain(toolChain)

        when:
        ResolvedTool<String> tool = resolver.resolve(String.class, Stub(Platform))

        then:
        tool.available

        when:
        tool.get()

        then:
        1 * provider.get(String.class)
    }

    private static interface TestCompileSpec extends CompileSpec {}
}
