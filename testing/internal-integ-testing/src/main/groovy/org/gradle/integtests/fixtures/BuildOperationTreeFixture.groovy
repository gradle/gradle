/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.integtests.fixtures

import junit.framework.AssertionFailedError
import org.gradle.api.specs.Spec
import org.gradle.api.specs.Specs
import org.gradle.internal.logging.events.StyledTextOutputEvent
import org.gradle.internal.operations.BuildOperationType
import org.gradle.internal.operations.BuildOperationTypes
import org.gradle.internal.operations.trace.BuildOperationRecord
import org.gradle.internal.operations.trace.BuildOperationTree

import java.util.regex.Pattern


class BuildOperationTreeFixture extends BuildOperationTreeQueries {

    private final BuildOperationTree operations

    BuildOperationTreeFixture(BuildOperationTree operations) {
        this.operations = operations
    }

    @Override
    List<BuildOperationRecord> getRoots() {
        operations.roots
    }

    @Override
    @SuppressWarnings("GrUnnecessaryPublicModifier")
    public <T extends BuildOperationType<?, ?>> BuildOperationRecord root(Class<T> type, Spec<? super BuildOperationRecord> predicate = Specs.satisfyAll()) {
        def detailsType = BuildOperationTypes.detailsType(type)
        def roots = operations.roots.findAll {
            it.detailsType && detailsType.isAssignableFrom(it.detailsType) && predicate.isSatisfiedBy(it)
        }
        assert roots.size() == 1
        return roots[0]
    }

    @Override
    @SuppressWarnings("GrUnnecessaryPublicModifier")
    public <T extends BuildOperationType<?, ?>> BuildOperationRecord first(Class<T> type, Spec<? super BuildOperationRecord> predicate = Specs.satisfyAll()) {
        def detailsType = BuildOperationTypes.detailsType(type)
        return operations.records.values().find {
            it.detailsType && detailsType.isAssignableFrom(it.detailsType) && predicate.isSatisfiedBy(it)
        }
    }

    @Override
    @SuppressWarnings("GrUnnecessaryPublicModifier")
    public <T extends BuildOperationType<?, ?>> boolean isType(BuildOperationRecord record, Class<T> type) {
        assert record.detailsType
        def detailsType = BuildOperationTypes.detailsType(type)
        detailsType.isAssignableFrom(record.detailsType)
    }

    @Override
    @SuppressWarnings("GrUnnecessaryPublicModifier")
    public <T extends BuildOperationType<?, ?>> List<BuildOperationRecord> all(Class<T> type, Spec<? super BuildOperationRecord> predicate = Specs.satisfyAll()) {
        def detailsType = BuildOperationTypes.detailsType(type)
        return operations.records.values().findAll {
            it.detailsType && detailsType.isAssignableFrom(it.detailsType) && predicate.isSatisfiedBy(it)
        }.toList()
    }

    @Override
    BuildOperationRecord first(Pattern displayName) {
        return operations.records.values().find { it.displayName ==~ displayName }
    }

    @Override
    List<BuildOperationRecord> all() {
        return operations.records.values().toList()
    }

    @Override
    List<BuildOperationRecord> all(Pattern displayName) {
        return operations.records.values().findAll { it.displayName ==~ displayName }.toList()
    }

    @Override
    BuildOperationRecord only(Pattern displayName) {
        def records = all(displayName)
        if (records.isEmpty()) {
            throw new AssertionFailedError("No operations found with display name that matches $displayName")
        } else if (records.size() > 1) {
            throw new AssertionFailedError("Multiple operations found with display name that matches $displayName. Expected 1, found ${records.size()}")
        }
        return records.first()
    }

    @Override
    BuildOperationRecord singleOrNone(Pattern displayName) {
        def records = all(displayName)
        assert records.size() <= 1
        return records.find()
    }

    @Override
    List<BuildOperationRecord> parentsOf(BuildOperationRecord child) {
        def parents = []
        def parentId = child.parentId
        while (parentId != null) {
            def parent = operations.records.get(parentId)
            parents.add(0, parent)
            parentId = parent.parentId
        }
        return parents
    }

    @Override
    void none(Pattern displayName) {
        def records = all(displayName)
        assert records.size() == 0
    }

    @Override
    void debugTree(
        Spec<? super BuildOperationRecord> predicate = Specs.SATISFIES_ALL,
        Spec<? super BuildOperationRecord> progressPredicate = Specs.SATISFIES_ALL
    ) {
        operations.roots.each { debugOpTree(it, 0, predicate, progressPredicate) }
    }

    private void debugOpTree(
        BuildOperationRecord op,
        int level,
        Spec<? super BuildOperationRecord> predicate,
        Spec<? super BuildOperationRecord> progressPredicate
    ) {
        if (predicate.isSatisfiedBy(op)) {
            println "${'  ' * level}(${op.displayName}, id: $op.id${op.detailsType ? ", details type: ${simpleClassName(op.detailsType)}" : ''})${op.details ? " $op.details" : ''}"
            if (progressPredicate.isSatisfiedBy(op)) {
                op.progress.each { p ->
                    def repr = p.hasDetailsOfType(StyledTextOutputEvent) ? p.details.spans*.text.join('') : "$p.detailsType.simpleName ${p.details?.toString() ?: ''}\n"
                    print "${'  ' * (level + 1)} $repr"
                }
            }
            op.children.each { debugOpTree(it, level + 1, predicate, progressPredicate) }
        }
    }

    private static String simpleClassName(Class<?> detailsType) {
        if (!detailsType) {
            return null
        } else {
            // Class.simpleName returns "" for certain anonymous classes and unhelpful things like "Details" for our op interfaces
            String clsName = detailsType.interfaces.length == 0 ? detailsType.name : detailsType.interfaces.first().name
            clsName.substring(clsName.lastIndexOf('.') + 1)
        }
    }
}
