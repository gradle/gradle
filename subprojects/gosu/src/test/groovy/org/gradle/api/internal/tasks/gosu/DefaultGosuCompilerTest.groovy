/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.tasks.gosu

import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.language.base.internal.compile.Compiler
import spock.lang.Specification

class DefaultGosuCompilerTest extends Specification {
    private final Compiler<GosuCompileSpec> gosuCompiler = Mock()
    private final FileCollection source = Mock()
    private final FileTree sourceTree = Mock()
    private final GosuCompileSpec spec = Mock()
    private final DefaultGosuCompiler compiler = new DefaultGosuCompiler(gosuCompiler)

    def executesGosuCompiler() {
        given:
        _ * spec.source >> source

        when:
        def result = compiler.execute(spec)

        then:
        result.didWork
        1 * gosuCompiler.execute(spec)
        //1 * source.getAsFileTree() >> sourceTree //FIXME:KM
    }

}
