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

import spock.lang.Specification
import org.gradle.api.tasks.WorkResult
import org.gradle.api.file.FileCollection

class IncrementalJavaSourceCompilerTest extends Specification {
    private final JavaSourceCompiler target = Mock()
    private final StaleClassCleaner cleaner = Mock()
    private final IncrementalJavaSourceCompiler compiler = new IncrementalJavaSourceCompiler(target) {
        protected StaleClassCleaner createCleaner() {
            return cleaner
        }
    }
    
    def cleansStaleClassesAndThenInvokesCompiler() {
        WorkResult result = Mock()
        File destDir = new File('dest')
        FileCollection source = Mock()
        compiler.destinationDir = destDir
        compiler.source = source

        when:
        def r = compiler.execute()

        then:
        r == result
        1 * cleaner.setDestinationDir(destDir)
        1 * cleaner.setSource(source)
        1 * cleaner.execute()
        1 * target.execute() >> result
    }
}
