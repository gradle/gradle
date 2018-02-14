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

package org.gradle.api.internal.tasks.compile.incremental

import org.gradle.api.internal.tasks.compile.JavaCompileSpec
import org.gradle.api.internal.tasks.compile.incremental.jar.JarClasspathSnapshotWriter
import org.gradle.api.tasks.WorkResult
import org.gradle.language.base.internal.compile.Compiler
import spock.lang.Specification
import spock.lang.Subject

class IncrementalResultStoringDecoratorTest extends Specification {

    def compiler = Mock(Compiler)
    def writer = Mock(JarClasspathSnapshotWriter)
    def infoUpdater = Mock(ClassSetAnalysisUpdater)
    def compileSpec = Stub(JavaCompileSpec)

    @Subject finalizer = new IncrementalResultStoringDecorator(compiler, writer, infoUpdater)

    def "performs finalization"() {
        when:
        finalizer.execute(compileSpec)

        then:
        1 * compiler.execute(compileSpec) >> Mock(WorkResult)
        1 * infoUpdater.updateAnalysis(compileSpec)
        1 * writer.storeJarSnapshots(_)
        0 * _
    }

    def "does not update if rebuild was not required"() {
        when:
        finalizer.execute(compileSpec)

        then:
        1 * compiler.execute(compileSpec) >> Mock(RecompilationNotNecessary)
        1 * writer.storeJarSnapshots(_)
        0 * _
    }
}
