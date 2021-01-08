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



package org.gradle.api.internal.tasks.compile.incremental.classpath


import org.gradle.api.internal.tasks.compile.incremental.deps.ClassSetAnalysis
import spock.lang.Specification
import spock.lang.Subject

class ClasspathSnapshotMakerTest extends Specification {

    def analysis = Mock(ClassSetAnalysis)
    def factory = Mock(ClasspathSnapshotFactory)

    @Subject maker = new ClasspathSnapshotMaker(factory)

    def "gets classpath snapshot"() {
        def jar1 = new File("jar1.jar")

        def classpathSnapshot = Stub(ClasspathSnapshot)
        def files = [jar1]

        when:
        maker.getClasspathSnapshot(files) == classpathSnapshot
        maker.getClasspathSnapshot(files) == classpathSnapshot

        then:
        1 * factory.createSnapshot(files) >> classpathSnapshot
        0 * _
    }
}
