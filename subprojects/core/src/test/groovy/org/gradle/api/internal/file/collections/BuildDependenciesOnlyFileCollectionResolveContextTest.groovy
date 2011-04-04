/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.file.collections

import org.gradle.api.internal.file.FileResolver
import org.gradle.api.tasks.TaskDependency
import spock.lang.Specification
import org.gradle.api.file.FileTree
import org.gradle.api.file.FileCollection

class BuildDependenciesOnlyFileCollectionResolveContextTest extends Specification {
    final BuildDependenciesOnlyFileCollectionResolveContext context = new BuildDependenciesOnlyFileCollectionResolveContext()

    def resolveAsBuildableReturnsEmptyListWhenContextIsEmpty() {
        expect:
        context.resolveAsBuildables() == []
    }

    def resolveAsBuildableIgnoresAMinimalFileCollection() {
        MinimalFileCollection fileCollection = Mock()

        when:
        context.add(fileCollection)
        def result = context.resolveAsBuildables()

        then:
        result.size() == 0
    }

    def resolveAsBuildableWrapsAMinimalFileCollectionWhichImplementsBuildableInAnEmptyFileTree() {
        TestFileSet fileCollection = Mock()
        TaskDependency buildDependency = Mock()

        when:
        context.add(fileCollection)
        def result = context.resolveAsBuildables()

        then:
        result.size() == 1
        result[0] instanceof FileTreeAdapter
        result[0].tree instanceof EmptyFileTree
        result[0].tree.buildDependencies == buildDependency
        1 * fileCollection.buildDependencies >> buildDependency
    }

    def resolveAsBuildableIgnoresAMinimalFileTree() {
        MinimalFileTree fileTree = Mock()

        when:
        context.add(fileTree)
        def result = context.resolveAsBuildables()

        then:
        result == []
    }

    def resolveAsBuildableWrapsAMinimalFileTreeWhichImplementsBuildable() {
        TestFileTree fileTree = Mock()
        TaskDependency dependency = Mock()

        when:
        context.add(fileTree)
        def result = context.resolveAsBuildables()

        then:
        result.size() == 1
        result[0] instanceof FileTreeAdapter
        result[0].tree instanceof EmptyFileTree
        result[0].tree.buildDependencies == dependency
        1 * fileTree.buildDependencies >> dependency
    }

    def resolveAsBuildablesForAFileCollection() {
        FileCollection fileCollection = Mock()
        TaskDependency dependency = Mock()

        when:
        context.add(fileCollection)
        def result = context.resolveAsBuildables()

        then:
        result.size() == 1
        result[0] instanceof FileTreeAdapter
        result[0].tree instanceof EmptyFileTree
        result[0].tree.buildDependencies == dependency
        1 * fileCollection.buildDependencies >> dependency
    }

    def resolveAsBuildablesDelegatesToACompositeFileCollection() {
        FileCollectionContainer composite = Mock()
        FileTree contents = Mock()

        when:
        context.add(composite)
        def result = context.resolveAsBuildables()

        then:
        result.size() == 1
        result[0] instanceof FileTreeAdapter
        result[0].tree instanceof EmptyFileTree
        1 * composite.resolve(!null) >> { it[0].add(contents) }
    }

    def resolveAsBuildablesWrapsATaskDependencyInAnEmptyFileTree() {
        TaskDependency dependency = Mock()

        when:
        context.add(dependency)
        def result = context.resolveAsBuildables()

        then:
        result.size() == 1
        result[0] instanceof FileTreeAdapter
        result[0].tree instanceof EmptyFileTree
        result[0].tree.buildDependencies == dependency
    }

    def resolveAsBuildablesIgnoresOtherTypes() {
        when:
        context.add('a')
        def result = context.resolveAsBuildables()

        then:
        result == []
    }

    def canPushContextWhenResolvingBuildables() {
        FileResolver fileResolver = Mock()
        TaskDependency dependency = Mock()

        when:
        context.push(fileResolver).add(dependency)
        def result = context.resolveAsBuildables()

        then:
        result.size() == 1
        result[0] instanceof FileTreeAdapter
        result[0].tree instanceof EmptyFileTree
        result[0].tree.buildDependencies == dependency
    }

    def pushedContextIgnoresOtherTypes() {
        FileResolver fileResolver = Mock()

        when:
        context.push(fileResolver).add('a')
        def result = context.resolveAsBuildables()

        then:
        result == []
    }

    def nestedContextIgnoresOtherTypes() {
        when:
        def nested = context.newContext()
        nested.add('a')
        def result = nested.resolveAsFileCollections()

        then:
        result == []
    }

    def file(String name) {
        File f = Mock()
        _ * f.file >> true
        _ * f.exists() >> true
        _ * f.canonicalFile >> f
        f
    }

    def directory(String name) {
        File f = Mock()
        _ * f.directory >> true
        _ * f.exists() >> true
        _ * f.canonicalFile >> f
        f
    }

    def nonExistent(String name) {
        File f = Mock()
        _ * f.canonicalFile >> f
        f
    }
}
