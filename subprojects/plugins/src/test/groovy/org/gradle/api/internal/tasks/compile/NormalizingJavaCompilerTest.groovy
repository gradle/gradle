/*
 * Copyright 2012 the original author or authors.
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
import org.gradle.api.tasks.WorkResult
import org.gradle.api.file.FileCollection
import org.gradle.api.InvalidUserDataException

class NormalizingJavaCompilerTest extends Specification {
    final JavaCompiler target = Mock()
    final NormalizingJavaCompiler compiler = new NormalizingJavaCompiler(target)

    def "delegates to target compiler"() {
        WorkResult workResult = Mock()
        compiler.source = files(new File("source.java"))
        compiler.classpath = files()

        when:
        def result = compiler.execute()

        then:
        result == workResult

        and:
        1 * target.execute() >> workResult
    }

    def "fails when a non-Java source file provided"() {
        compiler.source = files(new File("source.txt"))
        compiler.classpath = files()

        when:
        compiler.execute()

        then:
        InvalidUserDataException e = thrown()
        e.message == 'Cannot compile non-Java source file \'source.txt\'.'

        and:
        0 * target.execute()
    }

    def files(File... files) {
        FileCollection collection = Mock()
        _ * collection.files >> { files as Set }
        _ * collection.iterator() >> { (files as List).iterator() }
        return collection
    }
}
