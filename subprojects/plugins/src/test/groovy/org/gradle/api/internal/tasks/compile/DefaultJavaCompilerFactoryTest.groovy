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

import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.tasks.compile.CompileOptions
import org.gradle.internal.Factory
import spock.lang.Specification
import org.gradle.util.Jvm

class DefaultJavaCompilerFactoryTest extends Specification {

    CompileOptions compileOptionsMock = Mock();
    ProjectInternal projectInternal = Mock()
    Factory factoryMock = Mock()
    JavaCompilerFactory jcFactory = Mock();
    Jvm jvmMock = Mock(Jvm);
    DefaultJavaCompilerFactory factory = new DefaultJavaCompilerFactory(projectInternal, factoryMock, jcFactory, jvmMock);

    def "return Jdk7AwareCompiler if used jdk is 1.7"() {
        when:
        JavaCompiler compiler = factory.create(compileOptionsMock)
        then:
        1 * jvmMock.isJava7() >> true
        compiler instanceof Jdk7CompliantJavaCompiler
    }

    def "return Normal if used jdk is not 1.7"() {
        when:
        JavaCompiler compiler = factory.create(compileOptionsMock)
        then:
        1 * jvmMock.isJava7() >> false
        compiler instanceof NormalizingJavaCompiler
    }
}
