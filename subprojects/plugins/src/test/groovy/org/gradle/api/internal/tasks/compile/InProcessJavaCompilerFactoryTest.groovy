/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.gradle.api.tasks.compile.CompileOptions

import spock.lang.Specification

class InProcessJavaCompilerFactoryTest extends Specification {
    def factory = new InProcessJavaCompilerFactory()
    def options = new CompileOptions()
    
    @Requires(TestPrecondition.JDK6)
    def "creates JDK 6 compiler on JDK 6"() {
        expect:
        factory.create(options).getClass().name == "org.gradle.api.internal.tasks.compile.jdk6.Jdk6JavaCompiler"
    }

    @Requires(TestPrecondition.JDK5)
    def "creates Sun compiler on JDK 5"() {
        expect:
        factory.create(options) instanceof SunJavaCompiler
    }
}
