/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.api.internal.tasks.scala
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.internal.tasks.compile.JavaCompileSpec
import org.gradle.language.base.internal.compile.Compiler
import spock.lang.Specification

class DefaultScalaJavaJointCompilerTest extends Specification {
    private final Compiler<ScalaCompileSpec> scalaCompiler = Mock()
    private final Compiler<JavaCompileSpec> javaCompiler = Mock()
    private final FileCollection source = Mock()
    private final FileTree sourceTree = Mock()
    private final FileTree javaSource = Mock()
    private final ScalaJavaJointCompileSpec spec = Mock()
    private final DefaultScalaJavaJointCompiler compiler = new DefaultScalaJavaJointCompiler(scalaCompiler, javaCompiler)

    def executesScalaCompilerThenJavaCompiler() {
        given:
        _ * spec.source >> source

        when:
        def result = compiler.execute(spec)

        then:
        result.didWork
        1 * scalaCompiler.execute(spec)
        1 * source.getAsFileTree() >> sourceTree
        1 * sourceTree.matching(!null) >> javaSource
        javaSource.isEmpty() >> false
        1 * spec.setSource(javaSource)
        1 * javaCompiler.execute(spec)
    }

    def doesNotInvokeJavaCompilerWhenNoJavaSource() {
        _ * spec.source >> source

        when:
        def result = compiler.execute(spec)

        then:
        result.didWork
        1 * scalaCompiler.execute(spec)
        1 * source.getAsFileTree() >> sourceTree
        1 * sourceTree.matching(!null) >> javaSource
        _ * javaSource.isEmpty() >> true
        0 * javaCompiler._
    }
}
