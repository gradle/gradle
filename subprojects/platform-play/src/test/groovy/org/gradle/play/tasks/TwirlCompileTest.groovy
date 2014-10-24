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

package org.gradle.play.tasks

import org.gradle.play.internal.twirl.TwirlCompiler
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class TwirlCompileTest extends Specification {
    def project = ProjectBuilder.builder().build()
    TwirlCompile compile = project.tasks.create("twirlCompile", TwirlCompile)
    TwirlCompiler twirlCompiler = Mock(TwirlCompiler);

    def "invokes twirl compiler"(){
        given:
        def outputDir = Mock(File);
        compile.compiler = twirlCompiler
        compile.outputDirectory = outputDir
        compile.setCompilerClasspath(project.files([]))
        when:
        compile.compile()
        then:
        1 * twirlCompiler.execute(_)
    }
}
