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

import org.gradle.api.internal.tasks.compile.incremental.graph.ClassDependencyInfo
import org.gradle.api.internal.tasks.compile.incremental.graph.ClassDependencyInfoExtractor
import org.gradle.api.internal.tasks.compile.incremental.graph.ClassDependencyInfoSerializer
import org.gradle.api.tasks.compile.CompileOptions
import spock.lang.Specification
import spock.lang.Subject

class IncrementalCompilationSupportTest extends Specification {

    def options = Mock(CompileOptions)
    def extractor = Mock(ClassDependencyInfoExtractor)
    def serializer = Mock(ClassDependencyInfoSerializer)
    def feeder = Mock(JarSnapshotFeeder)

    @Subject support = new IncrementalCompilationSupport(feeder)

    def "analyzes class dependencies when incremental"() {
        options.incremental >> true
        def jars = [Mock(JarArchive)]

        when: support.compilationComplete(options, extractor, serializer, jars)
        then:
        1 * extractor.extractInfo("") >> Stub(ClassDependencyInfo)
        1 * serializer.writeInfo(_ as ClassDependencyInfo)
        1 * feeder.storeJarSnapshots(jars)
    }

    def "does nothing when not incremental"() {
        options.incremental >> false

        when: support.compilationComplete(options, extractor, serializer, [])
        then: 0 * extractor._
    }
}
