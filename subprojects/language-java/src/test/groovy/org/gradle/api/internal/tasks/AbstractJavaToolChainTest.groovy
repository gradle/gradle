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

package org.gradle.api.internal.tasks

import org.gradle.api.JavaVersion
import org.gradle.api.internal.tasks.compile.JavaCompileSpec
import org.gradle.api.tasks.javadoc.internal.JavadocGenerator
import org.gradle.api.tasks.javadoc.internal.JavadocSpec
import org.gradle.internal.logging.text.DiagnosticsVisitor
import org.gradle.jvm.internal.toolchain.JavaToolChainInternal
import org.gradle.jvm.platform.JavaPlatform
import org.gradle.jvm.platform.internal.DefaultJavaPlatform
import org.gradle.jvm.toolchain.internal.JavaCompilerFactory
import org.gradle.language.base.internal.compile.Compiler
import org.gradle.process.internal.ExecActionFactory
import org.gradle.util.Requires
import spock.lang.Specification

import static org.gradle.util.TestPrecondition.JDK8_OR_EARLIER

abstract class AbstractJavaToolChainTest extends Specification {
    def javaCompilerFactory = Stub(JavaCompilerFactory)
    def execActionFactory = Stub(ExecActionFactory)

    abstract JavaToolChainInternal getToolChain()

    abstract JavaVersion getToolChainJavaVersion()

    def "has reasonable string representation"() {
        expect:
        toolChain.name == "JDK${toolChainJavaVersion}"
        toolChain.displayName == "JDK ${toolChainJavaVersion.majorVersion} (${toolChainJavaVersion})"
        toolChain.toString() == toolChain.displayName
    }

    def "creates compiler for JavaCompileSpec"() {
        def compiler = Stub(Compiler)

        given:
        javaCompilerFactory.create(JavaCompileSpec.class) >> compiler

        expect:
        toolChain.select(platform(toolChainJavaVersion)).newCompiler(JavaCompileSpec.class) == compiler
    }

    def "creates compiler for JavadocSpec"() {
        expect:
        toolChain.select(platform(toolChainJavaVersion)).newCompiler(JavadocSpec.class) instanceof JavadocGenerator
    }

    def "creates available tool provider for earlier platform"() {
        def earlierPlatform = platform(JavaVersion.VERSION_1_5)

        when:
        def toolProvider = toolChain.select(earlierPlatform)

        then:
        toolProvider.available

        when:
        DiagnosticsVisitor visitor = Mock()
        toolProvider.explain(visitor)

        then:
        0 * _
    }

    @Requires(JDK8_OR_EARLIER)
    def "creates unavailable tool provider for incompatible platform"() {
        def futurePlatform = platform(JavaVersion.VERSION_1_9)
        DiagnosticsVisitor visitor = Mock()

        when:
        def toolProvider = toolChain.select(futurePlatform)

        then:
        !toolProvider.available

        when:
        toolProvider.explain(visitor)

        then:
        1 * visitor.node("Could not target platform: '${futurePlatform}' using tool chain: '${toolChain}'.")
        0 * _
    }

    def "toolchain version is the java major version"() {
        expect:
        toolChain.version == toolChainJavaVersion.majorVersion
    }

    static JavaPlatform platform(JavaVersion javaVersion) {
        return new DefaultJavaPlatform(javaVersion)
    }
}
