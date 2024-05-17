/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.internal.file

import org.gradle.api.Task
import org.gradle.api.internal.tasks.TaskDependencyContainer
import org.gradle.api.internal.tasks.TaskDependencyResolveContext

import java.util.function.Consumer

class CompositeFileCollectionSpec extends FileCollectionSpec {
    @Override
    AbstractFileCollection containing(File... files) {
        return new TestCollection() {
            @Override
            protected void visitChildren(Consumer<FileCollectionInternal> visitor) {
                visitor.accept(TestFiles.fileCollectionFactory().fixed(files))
            }
        }
    }

    def "visits contents on each query"() {
        def visited = 0;
        def collection = new TestCollection() {
            @Override
            protected void visitChildren(Consumer<FileCollectionInternal> visitor) {
                visited++
                visitor.accept(TestFiles.fileCollectionFactory().fixed(new File("foo")))
            }
        }

        when:
        def sourceCollections = collection.sourceCollections

        then:
        sourceCollections.size() == 1
        visited == 1

        when:
        def files = collection.files

        then:
        files.size() == 1
        visited == 2
    }

    def "visits contents when task dependencies are queried"() {
        def visited = 0;
        def task = Stub(Task)
        def dependency = Stub(Task)
        def child = collectionDependsOn(dependency)
        def collection = new TestCollection() {
            @Override
            protected void visitChildren(Consumer<FileCollectionInternal> visitor) {
                visited++
                visitor.accept(child)
            }
        }

        when:
        def dependencies = collection.buildDependencies

        then:
        visited == 0

        when:
        def deps = dependencies.getDependencies(task)

        then:
        visited == 1
        deps as List == [dependency]

        when:
        deps = dependencies.getDependencies(Stub(Task))

        then:
        visited == 2
        deps as List == [dependency]
    }

    def "subtype can avoid creating content when task dependencies are queried"() {
        def visited = 0;
        def task = Stub(Task)
        def dependency = Stub(Task)
        def collection = new TestCollection() {
            @Override
            void visitDependencies(TaskDependencyResolveContext context) {
                visited++
                context.add(dependency)
            }
        }

        when:
        def dependencies = collection.buildDependencies

        then:
        visited == 0

        when:
        def deps = dependencies.getDependencies(task)

        then:
        visited == 1
        deps as List == [dependency]

        when:
        deps = dependencies.getDependencies(Stub(Task))

        then:
        visited == 2
        deps as List == [dependency]
    }

    def "collection dependencies are live"() {
        def task = Stub(Task)
        def dependency1 = Stub(Task)
        def dependency2 = Stub(Task)
        def dependencySource = Mock(TaskDependencyContainer)

        def collection = new TestCollection() {
            @Override
            void visitDependencies(TaskDependencyResolveContext context) {
                context.add(dependencySource)
            }
        }

        given:
        1 * dependencySource.visitDependencies(_) >> { TaskDependencyResolveContext context -> context.add(dependency1) }
        1 * dependencySource.visitDependencies(_) >> { TaskDependencyResolveContext context -> context.add(dependency2) }
        1 * dependencySource.visitDependencies(_) >> { TaskDependencyResolveContext context -> context.add(dependency1); context.add(dependency2) }
        def dependencies = collection.buildDependencies

        expect:
        dependencies.getDependencies(task) as List == [dependency1]
        dependencies.getDependencies(task) as List == [dependency2]
        dependencies.getDependencies(task) as List == [dependency1, dependency2]
    }

    def "filtered collection has same live dependencies as original collection"() {
        def task = Stub(Task)
        def dependency1 = Stub(Task)
        def dependency2 = Stub(Task)
        def dependencySource = Mock(TaskDependencyContainer)

        def collection = new TestCollection() {
            @Override
            void visitDependencies(TaskDependencyResolveContext context) {
                context.add(dependencySource)
            }
        }

        given:
        1 * dependencySource.visitDependencies(_) >> { TaskDependencyResolveContext context -> context.add(dependency1) }
        1 * dependencySource.visitDependencies(_) >> { TaskDependencyResolveContext context -> context.add(dependency2) }

        def dependencies = collection.filter { false }.buildDependencies

        expect:
        dependencies.getDependencies(task) as List == [dependency1]
        dependencies.getDependencies(task) as List == [dependency2]
    }

    def "FileTree view has same live dependencies as original collection"() {
        def task = Stub(Task)
        def dependency1 = Stub(Task)
        def dependency2 = Stub(Task)
        def dependencySource = Mock(TaskDependencyContainer)

        def collection = new TestCollection() {
            @Override
            void visitDependencies(TaskDependencyResolveContext context) {
                context.add(dependencySource)
            }
        }

        given:
        1 * dependencySource.visitDependencies(_) >> { TaskDependencyResolveContext context -> context.add(dependency1) }
        1 * dependencySource.visitDependencies(_) >> { TaskDependencyResolveContext context -> context.add(dependency2) }

        def dependencies = collection.asFileTree.buildDependencies

        expect:
        dependencies.getDependencies(task) as List == [dependency1]
        dependencies.getDependencies(task) as List == [dependency2]
    }

    def "visits content of tree of collections"() {
        def child1 = new TestCollection() {
            @Override
            protected void visitChildren(Consumer<FileCollectionInternal> visitor) {
                visitor.accept(TestFiles.fileCollectionFactory().fixed(new File("1").absoluteFile))
            }
        }
        def child2 = new TestCollection() {
            @Override
            protected void visitChildren(Consumer<FileCollectionInternal> visitor) {
                visitor.accept(child1)
            }
        }
        def child3 = new TestCollection() {
            @Override
            protected void visitChildren(Consumer<FileCollectionInternal> visitor) {
                visitor.accept(TestFiles.fileCollectionFactory().fixed(new File("2").absoluteFile))
            }
        }
        def collection = new TestCollection() {
            @Override
            protected void visitChildren(Consumer<FileCollectionInternal> visitor) {
                visitor.accept(child2)
                visitor.accept(child3)
            }
        }

        expect:
        collection.files.size() == 2
    }

    def "visits content of tree of collections when dependencies are queried"() {
        def task = Stub(Task)
        def dependency1 = Stub(Task)
        def dependency2 = Stub(Task)
        def child1 = new TestCollection() {
            @Override
            protected void visitChildren(Consumer<FileCollectionInternal> visitor) {
                visitor.accept(collectionDependsOn(dependency1))
            }
        }
        def child2 = new TestCollection() {
            @Override
            protected void visitChildren(Consumer<FileCollectionInternal> visitor) {
                visitor.accept(child1)
            }
        }
        def child3 = new TestCollection() {
            @Override
            protected void visitChildren(Consumer<FileCollectionInternal> visitor) {
                visitor.accept(child2)
                visitor.accept(collectionDependsOn(dependency2))
            }
        }
        def collection = new TestCollection() {
            @Override
            protected void visitChildren(Consumer<FileCollectionInternal> visitor) {
                visitor.accept(child3)
            }
        }

        expect:
        collection.buildDependencies.getDependencies(task) == [dependency1, dependency2] as LinkedHashSet
    }

    def "descendant can avoid visiting content when task dependencies are queried"() {
        def task = Stub(Task)
        def dependency1 = Stub(Task)
        def dependency2 = Stub(Task)
        def child1 = new TestCollection() {
            @Override
            void visitDependencies(TaskDependencyResolveContext context) {
                context.add(dependency1)
            }
        }
        def child2 = new TestCollection() {
            @Override
            protected void visitChildren(Consumer<FileCollectionInternal> visitor) {
                visitor.accept(child1)
            }
        }
        def child3 = new TestCollection() {
            @Override
            protected void visitChildren(Consumer<FileCollectionInternal> visitor) {
                visitor.accept(child2)
                visitor.accept(collectionDependsOn(dependency2))
            }
        }
        def collection = new TestCollection() {
            @Override
            protected void visitChildren(Consumer<FileCollectionInternal> visitor) {
                visitor.accept(child3)
            }
        }

        expect:
        collection.buildDependencies.getDependencies(task) == [dependency1, dependency2] as LinkedHashSet
    }

    def "visits children when visitor requests contents"() {
        def child1 = Mock(FileCollectionInternal)
        def child2 = Mock(FileTreeInternal)
        def collection = new TestCollection() {
            @Override
            protected void visitChildren(Consumer<FileCollectionInternal> visitor) {
                visitor.accept(child1)
                visitor.accept(child2)
            }
        }
        def visitor = Mock(FileCollectionStructureVisitor)

        when:
        collection.visitStructure(visitor)

        then:
        1 * visitor.startVisit(FileCollectionInternal.OTHER, collection) >> true
        1 * child1.visitStructure(visitor)
        1 * child2.visitStructure(visitor)
        0 * _
    }

    def "does not visit children when visitor does not request contents"() {
        def child1 = Mock(FileCollectionInternal)
        def child2 = Mock(FileTreeInternal)
        def collection = new TestCollection() {
            @Override
            protected void visitChildren(Consumer<FileCollectionInternal> visitor) {
                visitor.accept(child1)
                visitor.accept(child2)
            }
        }
        def visitor = Mock(FileCollectionStructureVisitor)

        when:
        collection.visitStructure(visitor)

        then:
        1 * visitor.startVisit(FileCollectionInternal.OTHER, collection) >> false
        0 * _
    }

    def collectionDependsOn(Task... tasks) {
        def collection = Stub(FileCollectionInternal)
        collection.visitDependencies(_) >> { TaskDependencyResolveContext context ->
            for (t in tasks) {
                context.add(t)
            }
        }
        return collection
    }

    private static abstract class TestCollection extends CompositeFileCollection {
        @Override
        String getDisplayName() {
            return "<display-name>"
        }

        @Override
        protected void visitChildren(Consumer<FileCollectionInternal> visitor) {
            throw new UnsupportedOperationException();
        }
    }
}
