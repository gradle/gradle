/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.tasks;

import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshotter;
import org.gradle.api.internal.changedetection.state.GenericFileCollectionSnapshotter;
import org.gradle.api.internal.changedetection.state.SnapshotNormalizationStrategy;
import org.gradle.api.internal.changedetection.state.TaskFilePropertyCompareStrategy;
import org.gradle.api.internal.changedetection.state.TaskFilePropertySnapshotNormalizationStrategy;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskInputFilePropertyBuilder;
import org.gradle.api.tasks.TaskInputs;
import org.gradle.util.DeprecationLogger;

import java.util.Map;

import static org.gradle.api.internal.changedetection.state.TaskFilePropertyCompareStrategy.ORDERED;
import static org.gradle.api.internal.changedetection.state.TaskFilePropertyCompareStrategy.UNORDERED;
import static org.gradle.api.internal.changedetection.state.TaskFilePropertySnapshotNormalizationStrategy.ABSOLUTE;

public class DefaultTaskInputPropertySpec extends AbstractTaskPropertyBuilder implements TaskInputPropertySpecAndBuilder {

    private final TaskInputs taskInputs;
    private final TaskPropertyFileCollection files;
    private boolean skipWhenEmpty;
    private boolean optional;
    private TaskFilePropertyCompareStrategy compareStrategy = UNORDERED;
    private SnapshotNormalizationStrategy snapshotNormalizationStrategy = ABSOLUTE;
    private Class<? extends FileCollectionSnapshotter> snapshotter = GenericFileCollectionSnapshotter.class;

    public DefaultTaskInputPropertySpec(TaskInputs taskInputs, String taskName, boolean skipWhenEmpty, FileResolver resolver, Object paths) {
        this.taskInputs = taskInputs;
        this.files = new TaskPropertyFileCollection(taskName, "input", this, resolver, paths);
        this.skipWhenEmpty = skipWhenEmpty;
    }

    @Override
    public FileCollection getPropertyFiles() {
        return files;
    }

    @Override
    public TaskInputFilePropertyBuilderInternal withPropertyName(String propertyName) {
        setPropertyName(propertyName);
        return this;
    }

    public boolean isSkipWhenEmpty() {
        return skipWhenEmpty;
    }

    @Override
    public TaskInputFilePropertyBuilderInternal skipWhenEmpty(boolean skipWhenEmpty) {
        this.skipWhenEmpty = skipWhenEmpty;
        return this;
    }

    @Override
    public TaskInputFilePropertyBuilderInternal skipWhenEmpty() {
        return skipWhenEmpty(true);
    }

    public boolean isOptional() {
        return optional;
    }

    @Override
    public TaskInputFilePropertyBuilderInternal optional(boolean optional) {
        this.optional = optional;
        return this;
    }

    @Override
    public TaskInputFilePropertyBuilderInternal optional() {
        return optional(true);
    }

    @Override
    public TaskFilePropertyCompareStrategy getCompareStrategy() {
        return compareStrategy;
    }

    @Override
    public TaskInputFilePropertyBuilderInternal orderSensitive() {
        DeprecationLogger.nagUserOfDiscontinuedMethod("TaskInputFilePropertyBuilder.orderSensitive()");
        setOrderSensitive(true);
        return this;
    }

    @Override
    public TaskInputFilePropertyBuilderInternal orderSensitive(boolean orderSensitive) {
        DeprecationLogger.nagUserOfDiscontinuedMethod("TaskInputFilePropertyBuilder.orderSensitive(boolean)");
        setOrderSensitive(orderSensitive);
        return this;
    }

    private void setOrderSensitive(boolean orderSensitive) {
        this.compareStrategy = orderSensitive ? ORDERED : UNORDERED;
    }

    @Override
    public SnapshotNormalizationStrategy getSnapshotNormalizationStrategy() {
        return snapshotNormalizationStrategy;
    }

    @Override
    public TaskInputFilePropertyBuilderInternal withPathSensitivity(PathSensitivity sensitivity) {
        return withSnapshotNormalizationStrategy(TaskFilePropertySnapshotNormalizationStrategy.valueOf(sensitivity));
    }

    @Override
    public TaskInputFilePropertyBuilderInternal withSnapshotNormalizationStrategy(SnapshotNormalizationStrategy snapshotNormalizationStrategy) {
        this.snapshotNormalizationStrategy = snapshotNormalizationStrategy;
        return this;
    }

    @Override
    public TaskInputFilePropertyBuilderInternal withSnapshotter(Class<? extends FileCollectionSnapshotter> snapshotter) {
        this.snapshotter = snapshotter;
        return this;
    }

    @Override
    public Class<? extends FileCollectionSnapshotter> getSnapshotter() {
        return snapshotter;
    }

    @Override
    public String toString() {
        return getPropertyName() + " (" + compareStrategy + ", " + snapshotNormalizationStrategy + ")";
    }

    // --- Deprecated delegate methods

    private TaskInputs getTaskInputs(String method) {
        DeprecationLogger.nagUserOfDiscontinuedMethod("chaining of the " + method, String.format("Please use the %s method on TaskInputs directly instead.", method));
        return taskInputs;
    }

    @Override
    public boolean getHasInputs() {
        return getTaskInputs("getHasInputs()").getHasInputs();
    }

    @Override
    public FileCollection getFiles() {
        return getTaskInputs("getFiles()").getFiles();
    }

    @Override
    public TaskInputFilePropertyBuilder files(Object... paths) {
        return getTaskInputs("files(Object...)").files(paths);
    }

    @Override
    public TaskInputFilePropertyBuilder file(Object path) {
        return getTaskInputs("file(Object)").file(path);
    }

    @Override
    public TaskInputFilePropertyBuilder dir(Object dirPath) {
        return getTaskInputs("dir(Object)").dir(dirPath);
    }

    @Override
    public Map<String, Object> getProperties() {
        return getTaskInputs("getProperties()").getProperties();
    }

    @Override
    public TaskInputs property(String name, Object value) {
        return getTaskInputs("property(String, Object)").property(name, value);
    }

    @Override
    public TaskInputs properties(Map<String, ?> properties) {
        return getTaskInputs("properties(Map)").properties(properties);
    }

    @Override
    public boolean getHasSourceFiles() {
        return getTaskInputs("getHasSourceFiles()").getHasSourceFiles();
    }

    @Override
    public FileCollection getSourceFiles() {
        return getTaskInputs("getSourceFiles()").getSourceFiles();
    }

    @Override
    @Deprecated
    public TaskInputs source(Object... paths) {
        return getTaskInputs("source(Object...)").source(paths);
    }

    @Override
    @Deprecated
    public TaskInputs source(Object path) {
        return getTaskInputs("source(Object)").source(path);
    }

    @Override
    @Deprecated
    public TaskInputs sourceDir(Object path) {
        return getTaskInputs("sourceDir(Object)").sourceDir(path);
    }

    @Override
    public int compareTo(TaskPropertySpec o) {
        return getPropertyName().compareTo(o.getPropertyName());
    }
}
