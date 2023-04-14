/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.internal.artifacts

import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.file.UnionFileCollection
import spock.lang.Specification

class CachingDependencyResolveContextTest extends Specification {
    private final CachingDependencyResolveContext context = new CachingDependencyResolveContext(TestFiles.taskDependencyFactory(), true, null)

    def resolvesAFileCollection() {
        ResolvableDependency dependency = Mock()
        FileCollectionInternal fileCollection = Mock()

        when:
        context.add(dependency)
        def files = context.resolve()

        then:
        1 * dependency.resolve(context) >> { context.add(fileCollection) }
        files instanceof UnionFileCollection
        files.sources as List == [fileCollection]
    }

    def resolvesADependencyInternal() {
        ResolvableDependency dependency = Mock()
        ResolvableDependency otherDependency = Mock()
        FileCollectionInternal fileCollection = Mock()

        when:
        context.add(dependency)
        def files = context.resolve()

        then:
        1 * dependency.resolve(context) >> { context.add(otherDependency) }
        1 * otherDependency.resolve(context) >> { context.add(fileCollection) }
        files instanceof UnionFileCollection
        files.sources as List == [fileCollection]
    }

    def failsToResolveAnyOtherType() {
        ResolvableDependency dependency = Mock()

        when:
        context.add(dependency)
        context.resolve()

        then:
        1 * dependency.resolve(context) >> { context.add('thing') }
        def e = thrown(IllegalArgumentException)
        e.message == 'Cannot resolve object of unknown type String.'
    }

    def handlesCyclesBetweenDependencies() {
        ResolvableDependency dependency = Mock()
        ResolvableDependency otherDependency = Mock()
        FileCollectionInternal fileCollection = Mock()

        when:
        context.add(dependency)
        def files = context.resolve()

        then:
        1 * dependency.resolve(context) >> { context.add(otherDependency) }
        1 * otherDependency.resolve(context) >> { context.add(fileCollection); context.add(dependency) }
        files instanceof UnionFileCollection
        files.sources as List == [fileCollection]
    }
}
