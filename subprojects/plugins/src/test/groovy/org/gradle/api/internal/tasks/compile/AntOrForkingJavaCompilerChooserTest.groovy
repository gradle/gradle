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

import spock.lang.Specification

import org.gradle.api.tasks.compile.CompileOptions

class AntOrForkingJavaCompilerChooserTest extends Specification {
    def antCompiler = Mock(JavaCompiler)
    def forkingCompiler = Mock(JavaCompiler)
    def chooser = new AntOrForkingJavaCompilerChooser(antCompiler, forkingCompiler)
    def options = new CompileOptions()

    def "chooses Ant compiler when fork=false"() {
        options.fork = false

        expect:
        chooser.choose(options).is(antCompiler)
    }

    def "chooses Ant compiler when fork=true and useAntForking=true"() {
        options.fork = true
        options.forkOptions.useAntForking = true

        expect:
        chooser.choose(options).is(antCompiler)
    }

    def "chooses forking compiler when fork=true and useAntForking=false"() {
        options.fork = true
        options.forkOptions.useAntForking = false

        expect:
        chooser.choose(options).is(forkingCompiler)
    }
}
