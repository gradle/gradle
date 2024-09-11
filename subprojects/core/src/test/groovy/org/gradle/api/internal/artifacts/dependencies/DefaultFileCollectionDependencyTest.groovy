/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.artifacts.dependencies

import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.FileCollectionDependency
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.tasks.TaskDependency
import spock.lang.Specification

class DefaultFileCollectionDependencyTest extends Specification {
    private final FileCollectionInternal source = Mock()
    private final ComponentIdentifier targetComponent = Mock()
    private final DefaultFileCollectionDependency dependency = new DefaultFileCollectionDependency(targetComponent, source)

    def defaultValues() {
        expect:
        dependency.group == null
        dependency.name == "unspecified"
        dependency.version == null
    }

    def resolvesToTheSourceFileCollection() {
        final org.gradle.api.internal.artifacts.CachingDependencyResolveContext resolveContext = Mock()

        when:
        dependency.resolve(resolveContext)

        then:
        1 * resolveContext.add(source)
    }

    def usesSourceFileCollectionToResolveFiles() {
        final File file = new File("file")

        when:
        _ * source.files >> ([file] as Set)

        then:
        dependency.resolve() == [file] as Set
        dependency.resolve(true) == [file] as Set
        dependency.resolve(false) == [file] as Set
    }

    def createsCopy() {
        when:
        DefaultFileCollectionDependency copy = dependency.copy()

        then:
        copy.targetComponentId == targetComponent
        copy.files == source
        copy.contentEquals(dependency)
        dependency.contentEquals(copy)
    }

    def contentsAreEqualWhenFileSetsAreEqual() {
        given:
        FileCollectionDependency equalDependency = new DefaultFileCollectionDependency(source)
        FileCollectionDependency differentSource = new DefaultFileCollectionDependency(Mock(FileCollectionInternal))
        Dependency differentType = Mock(Dependency.class)

        expect:
        dependency.contentEquals(dependency)
        dependency.contentEquals(equalDependency)
        !dependency.contentEquals(differentSource)
        !dependency.contentEquals(differentType)
    }

    def usesSourceFileCollectionToDetermineBuildDependencies() {
        final TaskDependency taskDependency = Mock()

        when:
        1 * source.buildDependencies >> taskDependency

        then:
        dependency.buildDependencies == taskDependency
    }
}
