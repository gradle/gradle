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
package org.gradle.api.internal.tasks.compile

import org.gradle.language.jvm.internal.StaleClassCleaner
import spock.lang.Specification
import org.gradle.api.tasks.WorkResult
import org.gradle.api.file.FileCollection

class IncrementalJavaCompilerTest extends Specification {
    private final Compiler<JavaCompileSpec> target = Mock()
    private final JavaCompileSpec spec = Mock()
    private final StaleClassCleaner cleaner = Mock()
    private final IncrementalJavaCompilerSupport<JavaCompileSpec> compiler = new IncrementalJavaCompilerSupport<JavaCompileSpec>() {
        @Override
        protected Compiler<JavaCompileSpec> getCompiler() {
            return target
        }

        protected StaleClassCleaner createCleaner(JavaCompileSpec spec) {
            return cleaner
        }
    }
    
    def cleansStaleClassesAndThenInvokesCompiler() {
        WorkResult result = Mock()
        File destDir = new File('dest')
        FileCollection source = Mock()
        _ * spec.destinationDir >> destDir
        _ * spec.source >> source

        when:
        def r = compiler.execute(spec)

        then:
        r == result

        and:
        1 * cleaner.setDestinationDir(destDir)
        1 * cleaner.setSource(source)

        and:
        1 * cleaner.execute()

        and:
        1 * target.execute(spec) >> result
    }
}
