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

import spock.lang.Specification
import org.gradle.api.internal.tasks.compile.JavaCompiler
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree

class DefaultScalaJavaJointCompilerTest extends Specification {
    private final ScalaCompiler scalaCompiler = Mock()
    private final JavaCompiler javaCompiler = Mock()
    private final FileCollection source = Mock()
    private final FileTree sourceTree = Mock()
    private final FileTree javaSource = Mock()
    private final DefaultScalaJavaJointCompiler compiler = new DefaultScalaJavaJointCompiler(scalaCompiler, javaCompiler)

    def executesScalaCompilerThenJavaCompiler() {
        compiler.source = source

        when:
        def result = compiler.execute()

        then:
        result.didWork
        1 * scalaCompiler.execute()
        1 * source.getAsFileTree() >> sourceTree
        1 * sourceTree.matching(!null) >> javaSource
        _ * javaSource.isEmpty() >> false
        1 * javaCompiler.setSource(!null)
        1 * javaCompiler.execute()
    }

    def doesNotInvokeJavaCompilerWhenNoJavaSource() {
        compiler.source = source

        when:
        def result = compiler.execute()

        then:
        result.didWork
        1 * scalaCompiler.execute()
        1 * source.getAsFileTree() >> sourceTree
        1 * sourceTree.matching(!null) >> javaSource
        _ * javaSource.isEmpty() >> true
        0 * javaCompiler._
    }
}
