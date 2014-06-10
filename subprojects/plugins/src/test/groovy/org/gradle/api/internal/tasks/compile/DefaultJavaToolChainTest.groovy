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

package org.gradle.api.internal.tasks.compile

import org.gradle.api.tasks.javadoc.internal.JavadocGenerator
import org.gradle.api.tasks.javadoc.internal.JavadocSpec
import org.gradle.process.internal.ExecActionFactory
import spock.lang.Specification

class DefaultJavaToolChainTest extends Specification {
    def javaCompilerFactory = Stub(JavaCompilerFactory)
    def execActionFactory = Stub(ExecActionFactory)
    def toolChain = new DefaultJavaToolChain(javaCompilerFactory, execActionFactory)

    def "creates compiler for JavaCompileSpec"() {
        expect:
        toolChain.newCompiler(JavaCompileSpec) instanceof DelegatingJavaCompiler
    }

    def "creates compiler for JavadocSpec"() {
        expect:
        toolChain.newCompiler(JavadocSpec) instanceof JavadocGenerator
    }
}
