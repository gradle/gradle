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
import org.gradle.api.Action
import org.gradle.api.specs.Spec
import org.gradle.api.specs.Specs
import org.gradle.internal.logging.events.StyledTextOutputEvent
import org.gradle.internal.operations.BuildOperationType
import org.gradle.internal.operations.BuildOperationTypes
import org.gradle.internal.operations.trace.BuildOperationRecord
import org.intellij.lang.annotations.Language

import javax.annotation.Nonnull
import javax.annotation.Nullable
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.regex.Pattern

abstract class BuildOperationTreeQueries {

    abstract List<BuildOperationRecord> getRoots()

    abstract List<BuildOperationRecord> getRecords()

    abstract List<BuildOperationRecord> parentsOf(def child)

    abstract BuildOperationRecord withId(Long id)

    <T extends BuildOperationType<?, ?>> boolean isType(BuildOperationRecord record, Class<T> type) {
        assert record.detailsType
        def detailsType = BuildOperationTypes.detailsType(type)
        detailsType.isAssignableFrom(record.detailsType)
    }

    @SuppressWarnings("GrUnnecessaryPublicModifier")
    public <T extends BuildOperationType<?, ?>> BuildOperationRecord root(Class<T> type, Spec<? super BuildOperationRecord> predicate = Specs.satisfyAll()) {
        def detailsType = BuildOperationTypes.detailsType(type)
        def roots = getRoots().findAll {
            it.detailsType && detailsType.isAssignableFrom(it.detailsType) && predicate.isSatisfiedBy(it)
        }
        assert roots.size() == 1
        return roots[0]
    }

    <T extends BuildOperationType<?, ?>> List<TestableBuildOperationRecord> all(Class<T> type, Spec<? super BuildOperationRecord> predicate = Specs.satisfyAll()) {
        typed(type, predicate).collect { new TestableBuildOperationRecord(it) }
    }

    <T extends BuildOperationType<?, ?>> List<BuildOperationRecord> typed(Class<T> type, Spec<? super BuildOperationRecord> predicate = Specs.satisfyAll()) {
        def detailsType = BuildOperationTypes.detailsType(type)
        return getRecords().findAll {
            it.detailsType && detailsType.isAssignableFrom(it.detailsType) && predicate.isSatisfiedBy(it)
        }
    }

    @Nullable
    <T extends BuildOperationType<?, ?>> BuildOperationRecord first(Class<T> type, Spec<? super BuildOperationRecord> predicate = Specs.satisfyAll()) {
        def detailsType = BuildOperationTypes.detailsType(type)
        return getRecords().find {
            it.detailsType && detailsType.isAssignableFrom(it.detailsType) && predicate.isSatisfiedBy(it)
        }
    }

    @SuppressWarnings("GrUnnecessaryPublicModifier")
    public <T extends BuildOperationType<?, ?>> void none(Class<T> type, Spec<? super BuildOperationRecord> predicate = Specs.satisfyAll()) {
        assert typed(type, predicate).isEmpty()
    }

    @SuppressWarnings("GrUnnecessaryPublicModifier")
    public <T extends BuildOperationType<?, ?>> BuildOperationRecord only(Class<T> type, Spec<? super BuildOperationRecord> predicate = Specs.satisfyAll()) {
        def records = typed(type, predicate)
        assert records.size() == 1
        return records.first()
    }

    @SuppressWarnings("GrUnnecessaryPublicModifier")
    @Nullable
    public <T extends BuildOperationType<?, ?>> BuildOperationRecord singleOrNone(Class<T> type, Spec<? super BuildOperationRecord> predicate = Specs.satisfyAll()) {
        def records = typed(type, predicate)
        assert records.size() <= 1
        return records.find()
    }

    @Nullable
    BuildOperationRecord first(String displayName) {
        firstMatchingRegex(Pattern.quote(displayName))
    }

    @Nullable
    BuildOperationRecord firstMatchingRegex(@Language('regexp') String regex) {
        def pattern = Pattern.compile(regex)
        return getRecords().find { it.displayName ==~ pattern }
    }

    @Nonnull
    BuildOperationRecord only(String displayName) {
        return only(Pattern.compile(Pattern.quote(displayName)))
    }

    BuildOperationRecord only(Pattern displayName) {
        def records = records.findAll { it.displayName ==~ displayName }
        if (records.isEmpty()) {
            throw new AssertionFailedError("No operations found with display name that matches $displayName")
        } else if (records.size() > 1) {
            throw new AssertionFailedError("Multiple operations found with display name that matches $displayName. Expected 1, found ${records.size()}")
        }
        return records.first()
    }

    @Nullable
    BuildOperationRecord singleOrNone(String displayName) {
        return singleOrNone(Pattern.compile(Pattern.quote(displayName)))
    }

    @Nullable
    BuildOperationRecord singleOrNone(Pattern displayName) {
        def records = records.findAll { it.displayName ==~ displayName }
        assert records.size() <= 1
        return records.find()
    }

    void none(Pattern displayName) {
        def records = records.findAll { it.displayName ==~ displayName }
        assert records.size() == 0
    }

    void none(String displayName) {
        none(Pattern.compile(Pattern.quote(displayName)))
    }

    List<TestableBuildOperationRecord> matchingRegex(@Language('regexp') String regex) {
        def pattern = Pattern.compile(regex)
        return records.findAll { it.displayName ==~ pattern }.collect { new TestableBuildOperationRecord(it) }
    }

    boolean hasOperation(String displayName) {
        first(displayName) != null
    }

    @SuppressWarnings("GrUnnecessaryPublicModifier")
    public <T extends BuildOperationType<?, ?>> boolean hasOperation(Class<T> type) {
        first(type) != null
    }

    @SuppressWarnings(["GrMethodMayBeStatic", "GrUnnecessaryPublicModifier"])
    public <T extends BuildOperationType<?, ?>> List<BuildOperationRecord> search(BuildOperationRecord parent, Class<T> type, Spec<? super BuildOperationRecord> predicate = Specs.SATISFIES_ALL) {
        def detailsType = BuildOperationTypes.detailsType(type)
        Spec<BuildOperationRecord> typeSpec = {
            it.detailsType && detailsType.isAssignableFrom(it.detailsType)
        }
        search(parent, Specs.intersect(typeSpec, predicate))
    }

    @SuppressWarnings(["GrMethodMayBeStatic", "GrUnnecessaryPublicModifier"])
    public <T extends BuildOperationType<?, ?>> List<BuildOperationRecord> search(TestableBuildOperationRecord parent, Class<T> type, Spec<? super BuildOperationRecord> predicate = Specs.SATISFIES_ALL) {
        search(withId(parent.id), type, predicate)
    }

    @SuppressWarnings(["GrMethodMayBeStatic", "GrUnnecessaryPublicModifier"])
    public <T extends BuildOperationType<?, ?>> List<BuildOperationRecord> children(BuildOperationRecord parent, Class<T> type, Spec<? super BuildOperationRecord> predicate = Specs.SATISFIES_ALL) {
        Spec<BuildOperationRecord> parentSpec = {
            it.parentId == parent.id
        }
        return search(parent, type, Specs.intersect(parentSpec, predicate))
    }

    @SuppressWarnings(["GrMethodMayBeStatic", "GrUnnecessaryPublicModifier"])
    public <T extends BuildOperationType<?, ?>> List<BuildOperationRecord> children(TestableBuildOperationRecord parent, Class<T> type, Spec<? super BuildOperationRecord> predicate = Specs.SATISFIES_ALL) {
        return children(withId(parent.id), type, predicate)
    }

    @SuppressWarnings("GrMethodMayBeStatic")
    List<BuildOperationRecord> search(BuildOperationRecord parent, Spec<? super BuildOperationRecord> predicate = Specs.SATISFIES_ALL) {
        def matches = []
        parent.children.each {
            walk(it) {
                if (predicate.isSatisfiedBy(it)) {
                    matches << it
                }
            }
        }
        matches
    }

    List<BuildOperationRecord> search(TestableBuildOperationRecord parent, Spec<? super BuildOperationRecord> predicate = Specs.SATISFIES_ALL) {
        search(withId(parent.id), predicate)
    }

    List<BuildOperationRecord.Progress> progress(Class<?> clazz) {
        return getRecords().collect { it.progress(clazz) }.flatten()
    }

    void walk(Action<? super BuildOperationRecord> action) {
        roots.each { walk(it, action) }
    }

    @SuppressWarnings("GrMethodMayBeStatic")
    void walk(BuildOperationRecord parent, Action<? super BuildOperationRecord> action) {
        def search = new ConcurrentLinkedQueue<BuildOperationRecord>([parent])

        def operation = search.poll()
        while (operation != null) {
            action.execute(operation)
            search.addAll(operation.children)
            operation = search.poll()
        }
    }

    void orderedSerialSiblings(BuildOperationRecord... expectedOrder) {
        def expectedOrderList = expectedOrder.toList()
        assert expectedOrder*.parentId.unique().size() == 1
        def startTimeOrdered = expectedOrderList.sort(false) { it.startTime }
        assert expectedOrderList == startTimeOrdered
        def endTimeOrdered = expectedOrderList.sort(false) { it.endTime }
        assert endTimeOrdered == startTimeOrdered
    }


    private static class TimePoint implements Comparable<TimePoint> {
        private final boolean end
        private final long time
        private final BuildOperationRecord operation

        TimePoint(BuildOperationRecord operation, long time) {
            this(operation, time, false)
        }

        TimePoint(BuildOperationRecord operation, long time, boolean end) {
            this.operation = operation
            this.time = time
            this.end = end
        }

        @Override
        int compareTo(TimePoint o) {
            if (o.time > time) {
                return -1
            } else if (o.time < time) {
                return 1
            } else {
                if (end && o.end) {
                    return 0
                } else if (end) {
                    return -1
                } else {
                    return 1
                }
            }
        }

        @Override
        String toString() {
            if (end) {
                time + "E"
            } else {
                time + "S"
            }
        }
    }

    /**
     * Asserts that no more than maximumConcurrentOperations of the given type of build operation are executing at the same time.
     *
     * @param type type of build operation
     * @param maximumConcurrentOperations maximum concurrent operations allowed
     * @param concurrencyExpected whether or not to expect _any_ concurrency
     */
    void assertConcurrentOperationsDoNotExceed(Class<BuildOperationType> type, int maximumConcurrentOperations, boolean concurrencyExpected = false) {
        int maxConcurrency = getMaximumConcurrentOperations(type)
        assert maxConcurrency <= maximumConcurrentOperations
        if (concurrencyExpected) {
            assert maxConcurrency > 1: "No operations were executed concurrently"
        }
    }

    void assertConcurrentOperationsExecuted(Class<BuildOperationType> type) {
        assert getMaximumConcurrentOperations(type) > 1: "No operations were executed concurrently"
    }

    int getMaximumConcurrentOperations(Class<BuildOperationType> type) {
        def highWaterPoint = 0
        def allOperations = typed(type)

        List<TimePoint> points = []

        allOperations.each {
            points.add(new TimePoint(it, it.startTime))
            points.add(new TimePoint(it, it.endTime, true))
        }

        def concurrentOperations = []
        points.sort().each {
            if (it.end) {
                concurrentOperations.remove(it.operation)
            } else {
                if ((it.operation.endTime - it.operation.startTime) > 0) {
                    concurrentOperations.add(it.operation)
                }
            }
            if (concurrentOperations.size() > highWaterPoint) {
                highWaterPoint = concurrentOperations.size()
            }
        }
        return highWaterPoint
    }

    /**
     * Return a list of operations (possibly empty) that executed concurrently with the given operation.
     */
    List<BuildOperationRecord> getOperationsConcurrentWith(Class<BuildOperationType> type, BuildOperationRecord operation) {
        def concurrentOperations = []
        typed(type).each { candidate ->
            if (candidate != operation && candidate.startTime < operation.endTime && candidate.endTime > operation.startTime) {
                concurrentOperations << candidate
            }
        }
        return concurrentOperations
    }

    void debugTree(
        Spec<? super BuildOperationRecord> predicate = Specs.SATISFIES_ALL,
        Spec<? super BuildOperationRecord> progressPredicate = Specs.SATISFIES_ALL
    ) {
        getRoots().each { debugOpTree(it, 0, predicate, progressPredicate) }
    }

    protected void debugOpTree(
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

class TestableBuildOperationRecord {
    public final Long id // ignored for equality
    public final Long parentId
    public final String displayName
    public final Map<String, ?> details
    public final Map<String, ?> result;

    TestableBuildOperationRecord(
        Long id,
        String displayName,
        Long parentId,
        Map<String, ?> details,
        Map<String, ?> result
    ) {
        this.id = id
        this.parentId = parentId
        this.displayName = displayName
        this.details = details ?: [:]
        this.result = result ?: [:]
    }

    TestableBuildOperationRecord(BuildOperationRecord original) {
        this(original.id, original.displayName, original.parentId, original.details, original.result)
    }

    TestableBuildOperationRecord(
        String displayName,
        def parent,
        Map<String, ?> details,
        Map<String, ?> result
    ) {
        this(null, displayName, parent.id, details, result)
    }

    @Override
    boolean equals(Object o) {
        if (this.is(o)) {
            return true
        }
        if (getClass() != o.getClass()) {
            return false
        }

        TestableBuildOperationRecord that = (TestableBuildOperationRecord) o

        if (parentId != that.parentId) {
            return false
        }
        if (displayName != that.displayName) {
            return false
        }
        if (details.subMap(that.details.keySet()) != that.details) {
            return false
        }
        if (result.subMap(that.result.keySet()) != that.result) {
            return false
        }

        return true
    }

    @Override
    String toString() {
        return "TestableBuildOperationRecord{${id ? "id=$id, " : ""}parentId=$parentId, displayName='$displayName', details=$details, result=$result}"
    }

    static TestableBuildOperationRecord buildOp(Map params) {
        new TestableBuildOperationRecord(params.displayName, params.parent, params.details ?: [:], params.result ?: [:])
    }
}


