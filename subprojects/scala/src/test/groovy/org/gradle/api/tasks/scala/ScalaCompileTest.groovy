/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.tasks.scala

import org.apache.commons.io.FileUtils
import org.gradle.api.InvalidUserDataException
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.ConventionTask
import org.gradle.api.internal.file.FileTreeInternal
import org.gradle.api.internal.tasks.compile.processing.AnnotationProcessorPathFactory
import org.gradle.api.internal.tasks.scala.ScalaJavaJointCompileSpec
import org.gradle.api.tasks.TaskExecutionException
import org.gradle.api.tasks.compile.AbstractCompile
import org.gradle.api.tasks.compile.AbstractCompileTest
import org.gradle.language.base.internal.compile.Compiler
import org.gradle.language.scala.tasks.BaseScalaCompileOptions

class ScalaCompileTest extends AbstractCompileTest {
    private ScalaCompile scalaCompile

    private scalaCompiler = Mock(Compiler)
    private scalaClasspath = Mock(FileTreeInternal)
    private processorClasspath = Mock(FileCollection)
    private processorDetector = Mock(AnnotationProcessorPathFactory)

    @Override
    AbstractCompile getCompile() {
        return scalaCompile
    }

    @Override
    ConventionTask getTask() {
        return scalaCompile
    }

    def setup() {
        scalaCompile = createTask(ScalaCompile)
        scalaCompile.setCompiler(scalaCompiler)

        FileUtils.touch(new File(srcDir, "incl/file.scala"))
        FileUtils.touch(new File(srcDir, "incl/file.java"))
    }

    def "execute doing work"() {
        given:
        setUpMocksAndAttributes(scalaCompile)
        scalaClasspath.isEmpty() >> false

        when:
        execute(scalaCompile)

        then:
        1 * scalaCompiler.execute(_ as ScalaJavaJointCompileSpec)
    }

    def "moans if scalaClasspath is empty"() {
        given:
        setUpMocksAndAttributes(scalaCompile)
        scalaClasspath.isEmpty() >> true

        when:
        execute(scalaCompile)

        then:
        def e = thrown TaskExecutionException
        e.cause instanceof InvalidUserDataException
        e.cause.message.contains("'testTask.scalaClasspath' must not be empty")
    }

    def "sets annotation processor path"() {
        ScalaJavaJointCompileSpec compileSpec

        given:
        setUpMocksAndAttributes(scalaCompile)
        processorDetector.getEffectiveAnnotationProcessorClasspath(scalaCompile.getOptions(), scalaCompile.getClasspath()) >> processorClasspath

        when:
        execute(scalaCompile)

        then:
        1 * scalaCompiler.execute(_ as ScalaJavaJointCompileSpec) >> { args -> compileSpec = args[0]; null }
        compileSpec.getAnnotationProcessorPath() != null
    }

    protected void setUpMocksAndAttributes(final ScalaCompile compile) {
        super.setUpMocksAndAttributes(compile)
        compile.setScalaClasspath(scalaClasspath)
        compile.setZincClasspath(compile.getClasspath())
        BaseScalaCompileOptions options = compile.getScalaCompileOptions()
        options.getIncrementalOptions().setAnalysisFile(new File("analysisFile"))
    }
}
