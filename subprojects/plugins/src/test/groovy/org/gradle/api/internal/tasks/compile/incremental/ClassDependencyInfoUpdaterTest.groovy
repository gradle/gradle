/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.internal.file.FileOperations
import org.gradle.api.internal.tasks.compile.JavaCompileSpec
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassDependencyInfo
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassDependencyInfoExtractor
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassDependencyInfoWriter
import org.gradle.api.internal.tasks.compile.incremental.recomp.RecompilationNotNecessary
import org.gradle.api.tasks.WorkResult
import spock.lang.Specification
import spock.lang.Subject

class ClassDependencyInfoUpdaterTest extends Specification {

    def writer = Mock(ClassDependencyInfoWriter)
    def operations = Mock(FileOperations)
    def extractor = Mock(ClassDependencyInfoExtractor)
    def info = Mock(ClassDependencyInfo)

    @Subject updater = new ClassDependencyInfoUpdater(writer, operations, extractor)

    def "does not update info when recompilation was not necessary"() {
        def result = Stub(RecompilationNotNecessary)
        result.initialDependencyInfo >> info

        when:
        def out = updater.updateInfo(Mock(JavaCompileSpec), result)

        then:
        out == info
        0 * _
    }

    def "updates info"() {
        when:
        def out = updater.updateInfo(Stub(JavaCompileSpec), Mock(WorkResult))
        then:
        1 * operations.fileTree(_) >> Mock(ConfigurableFileTree)
        1 * extractor.getDependencyInfo() >> info
        1 * writer.writeInfo(_)

        out == info
    }
}
