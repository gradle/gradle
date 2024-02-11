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
import org.gradle.api.tasks.util.PatternFilterable
import spock.lang.Specification

import java.util.function.Consumer

class CompositeFileTreeSpec extends Specification {
    def "tree filtered by spec has same live dependencies as tree"() {
        def task = Stub(Task)
        def dependency1 = Stub(Task)
        def dependency2 = Stub(Task)
        def dependencySource = Mock(TaskDependencyContainer)

        def collection = new TestTree() {
            @Override
            void visitDependencies(TaskDependencyResolveContext context) {
                context.add(dependencySource)
            }
        }

        given:
        1 * dependencySource.visitDependencies(_) >> { TaskDependencyResolveContext context -> context.add(dependency1) }
        1 * dependencySource.visitDependencies(_) >> { TaskDependencyResolveContext context -> context.add(dependency2) }

        def dependencies = collection.matching { false }.buildDependencies

        expect:
        dependencies.getDependencies(task) as List == [dependency1]
        dependencies.getDependencies(task) as List == [dependency2]
    }

    def "tree filtered by pattern has same live dependencies as tree"() {
        def task = Stub(Task)
        def dependency1 = Stub(Task)
        def dependency2 = Stub(Task)
        def dependencySource = Mock(TaskDependencyContainer)

        def collection = new TestTree() {
            @Override
            void visitDependencies(TaskDependencyResolveContext context) {
                context.add(dependencySource)
            }
        }

        given:
        1 * dependencySource.visitDependencies(_) >> { TaskDependencyResolveContext context -> context.add(dependency1) }
        1 * dependencySource.visitDependencies(_) >> { TaskDependencyResolveContext context -> context.add(dependency2) }

        def dependencies = collection.matching(Stub(PatternFilterable)).buildDependencies

        expect:
        dependencies.getDependencies(task) as List == [dependency1]
        dependencies.getDependencies(task) as List == [dependency2]
    }

    private static abstract class TestTree extends CompositeFileTree {
        @Override
        String getDisplayName() {
            return "<display-name>"
        }

        @Override
        protected void visitChildren(Consumer<FileCollectionInternal> visitor) {
            throw new UnsupportedOperationException()
        }

        @Override
        void visitDependencies(TaskDependencyResolveContext context) {
            throw new UnsupportedOperationException()
        }
    }
}
