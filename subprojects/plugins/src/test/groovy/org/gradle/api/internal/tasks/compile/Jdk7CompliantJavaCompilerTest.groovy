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



package org.gradle.api.internal.tasks.compile

import org.gradle.api.tasks.compile.CompileOptions
import org.gradle.util.Jvm
import spock.lang.Specification

class Jdk7CompliantJavaCompilerTest extends Specification{

    def "with sourcelevel = jdk7 bootclasspath keeps clean"() {
            setup:
                JavaCompiler delegateCompiler = Mock()
                Jvm jvmMock = Mock();
                Jdk7CompliantJavaCompiler compiler = new Jdk7CompliantJavaCompiler(delegateCompiler, jvmMock)
                CompileOptions compileOptions = Mock();
                compiler.setCompileOptions(compileOptions)
                compiler.setSourceCompatibility(sourceCompatibility)
            when:
                compiler.execute()
            then:
                compileOptions.bootClasspath == null
            where:
                    sourceCompatibility << [null, "7", "1.7"]
        }

    def "manually defined bootstrap classpath isnt touched"(){
        setup:
            JavaCompiler delegateCompiler = Mock()
            Jdk7CompliantJavaCompiler compiler = new Jdk7CompliantJavaCompiler(delegateCompiler)
            CompileOptions compileOptions = Mock();
            compiler.setCompileOptions(compileOptions)
            compileOptions.bootClasspath >> "dummy.jar"
        when:
            compiler.execute()
        then:
            compileOptions.bootClasspath == "dummy.jar"
    }

    def "classpath is augmented with rt.jar when sourcelevel!=jdk7 and bootstrap = null "() {
            setup:
                JavaCompiler delegateCompiler = Mock()
                Jvm jvmMock = Mock();
                File rtJar = Mock();
                Jdk7CompliantJavaCompiler compiler = new Jdk7CompliantJavaCompiler(delegateCompiler, jvmMock)
                CompileOptions compileOptions = Mock();
                compiler.setSourceCompatibility(sourceCompatibility)
                compiler.setCompileOptions(compileOptions)
                rtJar.getAbsolutePath() >> "rt.jar"
                jvmMock.getRuntimeJar() >> rtJar
            when:
                compiler.execute()
            then:
                1 * jvmMock.getRuntimeJar() >> rtJar
            where:
                sourceCompatibility << ["1.5", "5", "1.6", "6"]
    }
}
