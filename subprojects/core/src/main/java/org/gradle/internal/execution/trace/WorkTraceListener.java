/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.execution.trace;

import com.google.common.collect.ImmutableSortedMap;
import groovy.json.JsonOutput;
import org.gradle.api.Task;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.project.ProjectState;
import org.gradle.api.internal.project.taskfactory.TaskIdentity;
import org.gradle.api.internal.tasks.SnapshotTaskInputsBuildOperationType;
import org.gradle.api.internal.tasks.execution.ExecuteTaskBuildOperationDetails;
import org.gradle.api.internal.tasks.execution.ExecuteTaskBuildOperationResult;
import org.gradle.api.internal.tasks.properties.InputFilePropertySpec;
import org.gradle.api.internal.tasks.properties.OutputFilePropertySpec;
import org.gradle.api.internal.tasks.properties.TaskProperties;
import org.gradle.execution.plan.LocalTaskNode;
import org.gradle.execution.plan.Node;
import org.gradle.execution.plan.TaskInAnotherBuild;
import org.gradle.execution.plan.TaskNode;
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.classpath.Instrumented;
import org.gradle.internal.file.FileType;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationListener;
import org.gradle.internal.operations.OperationFinishEvent;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.operations.OperationProgressEvent;
import org.gradle.internal.operations.OperationStartEvent;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.RelativePathTracker;
import org.gradle.internal.snapshot.SnapshotVisitResult;
import org.gradle.internal.taskgraph.CalculateTaskGraphBuildOperationType;
import org.gradle.internal.taskgraph.CalculateTreeTaskGraphBuildOperationType;
import org.gradle.internal.taskgraph.NodeIdentity;
import org.gradle.util.Path;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Turns build operations into work trace records.
 * <p>
 * Records are JSON objects with a {@code kind} discriminator:
 * <ul>
 * <li>{@code projects}: maps every project identity path in the build tree to its directory (written once, before the first task record)</li>
 * <li>{@code plan}: the scheduled task graph of one build (requested/excluded task paths and the planned tasks with their edges)</li>
 * <li>{@code task}: one executed task with its scheduling provenance, inputs and outputs</li>
 * </ul>
 * File locations are project-relative when inside some project directory (deepest wins) and absolute otherwise.
 * Thread-safe: task operations finish on many threads.
 */
class WorkTraceListener implements BuildOperationListener {

    private final boolean enabled;
    private final Consumer<List<String>> planSink;
    private final Consumer<String> workSink;
    private final BuildStateRegistry buildStateRegistry;
    private final Map<OperationIdentifier, CapturedInputs> inputsByTaskOperation = new ConcurrentHashMap<>();
    private final Map<OperationIdentifier, InputDependencies> inputDependenciesByTaskOperation = new ConcurrentHashMap<>();
    private final Map<String, List<String>> outputRootPathsByTask = new ConcurrentHashMap<>();
    private final List<String> pendingPlanLines = new ArrayList<>();
    private volatile @Nullable List<ProjectDirectory> projectDirectories;

    /**
     * @param enabled whether the trace is written; when false only the option is recorded as a configuration input
     * @param planSink receives all {@code plan} records of the build tree at once, in build order
     * @param workSink receives the {@code projects} and {@code task} records
     */
    WorkTraceListener(boolean enabled, Consumer<List<String>> planSink, Consumer<String> workSink, BuildStateRegistry buildStateRegistry) {
        this.enabled = enabled;
        this.planSink = planSink;
        this.workSink = workSink;
        this.buildStateRegistry = buildStateRegistry;
    }

    @Override
    public void started(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent) {
        Object details = buildOperation.getDetails();
        if (enabled && details instanceof ExecuteTaskBuildOperationDetails) {
            // Resolved before the task runs: executing a task finalizes its property values, and a finalized
            // file collection no longer carries the task dependencies it was built from
            ExecuteTaskBuildOperationDetails taskDetails = (ExecuteTaskBuildOperationDetails) details;
            inputDependenciesByTaskOperation.put(buildOperation.getId(), resolveInputDependencies(taskDetails.getTaskNode(), taskDetails.getTask()));
        }
    }

    @Override
    public void progress(OperationIdentifier operationIdentifier, OperationProgressEvent progressEvent) {
    }

    @Override
    public void finished(BuildOperationDescriptor buildOperation, OperationFinishEvent finishEvent) {
        Object details = buildOperation.getDetails();
        Object result = finishEvent.getResult();
        if (details instanceof CalculateTreeTaskGraphBuildOperationType.Details) {
            // Runs while the configuration cache fingerprint is being collected: record the option as a configuration input
            // on every build, so that turning the trace on or off later invalidates the entry, and pin the plan file.
            Instrumented.systemProperty(WorkTraceService.SYSPROP, WorkTraceListener.class.getName());
            flushPlan();
            return;
        }
        if (!enabled) {
            return;
        }
        if (details instanceof CalculateTaskGraphBuildOperationType.Details && result instanceof CalculateTaskGraphBuildOperationType.Result) {
            writePlan((CalculateTaskGraphBuildOperationType.Details) details, (CalculateTaskGraphBuildOperationType.Result) result);
        } else if (result instanceof SnapshotTaskInputsBuildOperationType.Result) {
            captureInputs(buildOperation.getParentId(), (SnapshotTaskInputsBuildOperationType.Result) result);
        } else if (details instanceof ExecuteTaskBuildOperationDetails) {
            ExecuteTaskBuildOperationResult taskResult = result instanceof ExecuteTaskBuildOperationResult ? (ExecuteTaskBuildOperationResult) result : null;
            writeTask(buildOperation.getId(), (ExecuteTaskBuildOperationDetails) details, taskResult);
        }
    }

    // Plan record

    private void writePlan(CalculateTaskGraphBuildOperationType.Details details, CalculateTaskGraphBuildOperationType.Result result) {
        // Sorted, so that the plan file is deterministic for a given configuration (it is a configuration cache input)
        List<Map<String, Object>> tasks = new ArrayList<>();
        for (CalculateTaskGraphBuildOperationType.PlannedTask plannedTask : result.getTaskPlan()) {
            Map<String, Object> task = new LinkedHashMap<>();
            task.put("buildTreeTaskPath", buildTreeTaskPathOf(plannedTask.getTask()));
            List<String> dependencies = new ArrayList<>();
            for (NodeIdentity dependency : plannedTask.getNodeDependencies()) {
                if (dependency instanceof CalculateTaskGraphBuildOperationType.TaskIdentity) {
                    dependencies.add(buildTreeTaskPathOf((CalculateTaskGraphBuildOperationType.TaskIdentity) dependency));
                }
            }
            Collections.sort(dependencies);
            task.put("dependencies", dependencies);
            task.put("mustRunAfter", buildTreeTaskPathsOf(plannedTask.getMustRunAfter()));
            task.put("shouldRunAfter", buildTreeTaskPathsOf(plannedTask.getShouldRunAfter()));
            task.put("finalizedBy", buildTreeTaskPathsOf(plannedTask.getFinalizedBy()));
            tasks.add(task);
        }
        tasks.sort(Comparator.comparing(task -> (String) task.get("buildTreeTaskPath")));

        Map<String, Object> record = new LinkedHashMap<>();
        record.put("kind", "plan");
        record.put("buildPath", details.getBuildPath());
        record.put("requestedTaskPaths", buildTreeTaskPathsOf(details.getBuildPath(), result.getRequestedTaskPaths()));
        record.put("excludedTaskPaths", buildTreeTaskPathsOf(details.getBuildPath(), result.getExcludedTaskPaths()));
        record.put("tasks", tasks);
        synchronized (pendingPlanLines) {
            pendingPlanLines.add(JsonOutput.toJson(record));
        }
    }

    /**
     * Writes the plan records collected since the last flush, if any. The task graphs of all builds in the tree are
     * calculated within one "Calculate build tree task graph" operation, so this normally runs once per build that calculates them.
     * On a configuration cache hit no plan record is produced and the plan file is left untouched.
     */
    void flushPlan() {
        List<String> lines;
        synchronized (pendingPlanLines) {
            if (pendingPlanLines.isEmpty()) {
                return;
            }
            lines = new ArrayList<>(pendingPlanLines);
            pendingPlanLines.clear();
        }
        planSink.accept(lines);
    }

    // Input snapshot (child operation of the task operation)

    private void captureInputs(@Nullable OperationIdentifier taskOperationId, SnapshotTaskInputsBuildOperationType.Result result) {
        if (taskOperationId == null) {
            return;
        }
        InputFilePropertyCollector collector = new InputFilePropertyCollector();
        result.visitInputFileProperties(collector);

        Map<String, Object> inputValueHashes = null;
        Map<String, byte[]> inputValueHashesBytes = result.getInputValueHashesBytes();
        if (inputValueHashesBytes != null) {
            inputValueHashes = new LinkedHashMap<>();
            for (Map.Entry<String, byte[]> entry : inputValueHashesBytes.entrySet()) {
                inputValueHashes.put(entry.getKey(), hex(entry.getValue()));
            }
        }
        inputsByTaskOperation.put(taskOperationId, new CapturedInputs(collector.properties, collector.rootPathsByProperty, inputValueHashes, hex(result.getHashBytes())));
    }

    private static final class CapturedInputs {
        final List<Object> inputFileProperties;
        final Map<String, List<String>> rootPathsByProperty;
        final @Nullable Map<String, Object> inputValueHashes;
        final @Nullable String cacheKey;

        CapturedInputs(List<Object> inputFileProperties, Map<String, List<String>> rootPathsByProperty, @Nullable Map<String, Object> inputValueHashes, @Nullable String cacheKey) {
            this.inputFileProperties = inputFileProperties;
            this.rootPathsByProperty = rootPathsByProperty;
            this.inputValueHashes = inputValueHashes;
            this.cacheKey = cacheKey;
        }
    }

    /**
     * Collects each input file property with its attributes and the locations of its roots.
     */
    private final class InputFilePropertyCollector implements SnapshotTaskInputsBuildOperationType.Result.InputFilePropertyVisitor {
        final List<Object> properties = new ArrayList<>();
        final Map<String, List<String>> rootPathsByProperty = new LinkedHashMap<>();
        private @Nullable List<Object> currentRoots;
        private @Nullable List<String> currentRootPaths;

        @Override
        public void preProperty(SnapshotTaskInputsBuildOperationType.Result.VisitState state) {
            Map<String, Object> property = new LinkedHashMap<>();
            property.put("property", state.getPropertyName());
            property.put("attributes", new ArrayList<>(state.getPropertyAttributes()));
            currentRoots = new ArrayList<>();
            currentRootPaths = new ArrayList<>();
            property.put("roots", currentRoots);
            properties.add(property);
            rootPathsByProperty.put(state.getPropertyName(), currentRootPaths);
        }

        @Override
        public void preRoot(SnapshotTaskInputsBuildOperationType.Result.VisitState state) {
            File root = new File(state.getPath());
            requireNonNull(currentRoots).add(locationOf(root));
            requireNonNull(currentRootPaths).add(root.getAbsolutePath());
        }

        @Override
        public void preDirectory(SnapshotTaskInputsBuildOperationType.Result.VisitState state) {
        }

        @Override
        public void file(SnapshotTaskInputsBuildOperationType.Result.VisitState state) {
        }

        @Override
        public void postDirectory() {
        }

        @Override
        public void postRoot() {
        }

        @Override
        public void postProperty() {
            currentRoots = null;
            currentRootPaths = null;
        }
    }

    // Task record

    private void writeTask(OperationIdentifier operationId, ExecuteTaskBuildOperationDetails details, @Nullable ExecuteTaskBuildOperationResult result) {
        ensureProjectsRecordWritten();
        CapturedInputs inputs = inputsByTaskOperation.remove(operationId);
        LocalTaskNode node = details.getTaskNode();
        TaskInternal task = details.getTask();
        TaskIdentity<?> identity = task.getTaskIdentity();

        String buildTreeTaskPath = task.getIdentityPath().asString();
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("kind", "task");
        record.put("buildTreeTaskPath", buildTreeTaskPath);
        record.put("implementation", identity.getTaskType().getName());
        record.put("caching", caching(result, inputs));
        InputDependencies inputDependencies = inputDependenciesByTaskOperation.remove(operationId);
        if (inputDependencies == null) {
            inputDependencies = resolveInputDependencies(node, task);
        }
        if (inputDependencies.entries.isEmpty() && inputs != null) {
            inputDependencies = inferInputDependenciesFromLocations(node, inputs);
        }
        record.put("scheduling", scheduling(node, inputDependencies));
        record.put("inputs", inputs == null ? null : inputs.inputFileProperties);
        record.put("inputValueHashes", inputs == null ? null : inputs.inputValueHashes);
        record.put("outputs", outputs(node, result == null ? null : result.getOutputFileSnapshots()));
        record.put("inputsCaptured", inputs != null);
        outputRootPathsByTask.put(buildTreeTaskPath, outputRootPaths(node));
        emit(record);
    }

    /**
     * Fallback for nodes restored from the configuration cache, whose property values no longer carry task dependencies:
     * an input root that is, contains or is inside an output root of a task this node depends on is attributed to that task.
     * Producers always complete (and are written) before their consumers start.
     */
    private InputDependencies inferInputDependenciesFromLocations(LocalTaskNode node, CapturedInputs inputs) {
        List<String> dependencyPaths = nodeTaskPaths(node.getDependencySuccessors());
        List<Object> entries = new ArrayList<>();
        for (Map.Entry<String, List<String>> property : inputs.rootPathsByProperty.entrySet()) {
            List<String> producers = new ArrayList<>();
            for (String dependencyPath : dependencyPaths) {
                List<String> outputRoots = outputRootPathsByTask.get(dependencyPath);
                if (outputRoots != null && overlaps(property.getValue(), outputRoots)) {
                    producers.add(dependencyPath);
                }
            }
            if (!producers.isEmpty()) {
                Collections.sort(producers);
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("property", property.getKey());
                entry.put("tasks", producers);
                entries.add(entry);
            }
        }
        return new InputDependencies(entries, null);
    }

    private static boolean overlaps(List<String> inputRoots, List<String> outputRoots) {
        for (String inputRoot : inputRoots) {
            for (String outputRoot : outputRoots) {
                if (inputRoot.equals(outputRoot) || isInside(inputRoot, outputRoot) || isInside(outputRoot, inputRoot)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isInside(String path, String directory) {
        return path.startsWith(directory + File.separator);
    }

    private static List<String> outputRootPaths(LocalTaskNode node) {
        List<String> roots = new ArrayList<>();
        for (OutputFilePropertySpec spec : node.getTaskProperties().getOutputFileProperties()) {
            File root = spec.getOutputFile();
            if (root != null) {
                roots.add(root.getAbsolutePath());
            }
        }
        return roots;
    }


    private static Map<String, Object> caching(@Nullable ExecuteTaskBuildOperationResult result, @Nullable CapturedInputs inputs) {
        Map<String, Object> caching = new LinkedHashMap<>();
        caching.put("key", inputs == null ? null : inputs.cacheKey);
        caching.put("disabledReason", result == null ? null : result.getCachingDisabledReasonMessage());
        caching.put("disabledReasonCategory", result == null ? null : result.getCachingDisabledReasonCategory());
        // The origin build invocation id is deliberately left out: it identifies the build that produced reused outputs,
        // so it varies between otherwise identical traces and would reveal the task outcome.
        return caching;
    }

    private static Map<String, Object> scheduling(LocalTaskNode node, InputDependencies inputDependencies) {
        Map<String, Object> scheduling = new LinkedHashMap<>();
        // Entry nodes are restored from the configuration cache, so this does not depend on a plan record having been seen in this build
        scheduling.put("requested", node.isEntryNode());
        scheduling.put("explicitDependsOn", nodeTaskPaths(node.getLifecycleSuccessors()));
        scheduling.put("inputDependencies", inputDependencies.entries);
        if (inputDependencies.error != null) {
            scheduling.put("inputDependenciesError", inputDependencies.error);
        }
        scheduling.put("finalizerOf", nodeTaskPaths(node.getFinalizingSuccessors()));
        scheduling.put("mustRunAfter", nodeTaskPaths(node.getMustSuccessors()));
        scheduling.put("shouldRunAfter", nodeTaskPaths(node.getShouldSuccessors()));
        return scheduling;
    }

    private static final class InputDependencies {
        final List<Object> entries;
        final @Nullable String error;

        InputDependencies(List<Object> entries, @Nullable String error) {
            this.entries = entries;
            this.error = error;
        }
    }

    private static InputDependencies resolveInputDependencies(LocalTaskNode node, TaskInternal task) {
        try {
            return new InputDependencies(inputDependencies(node.getTaskProperties(), task), null);
        } catch (RuntimeException e) {
            return new InputDependencies(Collections.emptyList(), String.valueOf(e));
        }
    }

    /**
     * Mirrors how {@code DefaultTaskInputs.visitDependencies} infers task dependencies from input file collections.
     */
    private static List<Object> inputDependencies(TaskProperties taskProperties, TaskInternal task) {
        List<Object> inputDependencies = new ArrayList<>();
        for (InputFilePropertySpec spec : taskProperties.getInputFileProperties()) {
            Set<? extends Task> producers = spec.getPropertyFiles().getBuildDependencies().getDependencies(task);
            if (producers.isEmpty()) {
                continue;
            }
            List<String> paths = new ArrayList<>();
            for (Task producer : producers) {
                paths.add(((TaskInternal) producer).getIdentityPath().asString());
            }
            Collections.sort(paths);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("property", spec.getPropertyName());
            entry.put("tasks", paths);
            inputDependencies.add(entry);
        }
        return inputDependencies;
    }

    private List<Object> outputs(LocalTaskNode node, @Nullable ImmutableSortedMap<String, FileSystemSnapshot> snapshots) {
        List<Object> outputs = new ArrayList<>();
        for (OutputFilePropertySpec spec : node.getTaskProperties().getOutputFileProperties()) {
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("property", spec.getPropertyName());
            output.put("type", spec.getOutputType().name());
            File root = spec.getOutputFile();
            output.put("location", root == null ? null : locationOf(root));
            FileSystemSnapshot snapshot = snapshots == null ? null : snapshots.get(spec.getPropertyName());
            output.put("files", snapshot == null ? Collections.emptyList() : files(snapshot));
            outputs.add(output);
        }
        return outputs;
    }

    /**
     * The regular files under the snapshot as paths relative to its root ({@code ""} when the root is the file itself).
     */
    private static List<String> files(FileSystemSnapshot snapshot) {
        List<String> files = new ArrayList<>();
        snapshot.accept(new RelativePathTracker(), (entry, relativePath) -> {
            if (entry.getType() == FileType.RegularFile) {
                files.add(relativePath.isRoot() ? "" : relativePath.toRelativePath());
            }
            return SnapshotVisitResult.CONTINUE;
        });
        return files;
    }

    // Projects record and locations

    private static final class ProjectDirectory {
        final String identityPath;
        final String absoluteDir;

        ProjectDirectory(String identityPath, String absoluteDir) {
            this.identityPath = identityPath;
            this.absoluteDir = absoluteDir;
        }
    }

    /**
     * Collects the project directories of the whole build tree once, and writes the {@code projects} record.
     * Projects are sorted deepest directory first so that the first containing directory wins.
     * Called before the first task record, and by the service at build completion when no task ran (e.g. {@code --dry-run}).
     */
    synchronized void ensureProjectsRecordWritten() {
        if (projectDirectories != null) {
            return;
        }
        List<ProjectDirectory> directories = new ArrayList<>();
        buildStateRegistry.visitBuilds(build -> {
            if (!build.isProjectsLoaded()) {
                return;
            }
            for (ProjectState project : build.getProjects().getAllProjects()) {
                directories.add(new ProjectDirectory(project.getIdentityPath().asString(), project.getProjectDir().getAbsolutePath()));
            }
        });

        List<Object> projects = new ArrayList<>();
        directories.sort(Comparator.comparing((ProjectDirectory directory) -> directory.identityPath));
        for (ProjectDirectory directory : directories) {
            Map<String, Object> project = new LinkedHashMap<>();
            project.put("project", directory.identityPath);
            project.put("dir", directory.absoluteDir);
            projects.add(project);
        }
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("kind", "projects");
        record.put("projects", projects);

        directories.sort(Comparator.comparingInt((ProjectDirectory directory) -> directory.absoluteDir.length()).reversed());
        projectDirectories = Collections.unmodifiableList(directories);
        emit(record);
    }

    private Map<String, Object> locationOf(File file) {
        String absolutePath = file.getAbsolutePath();
        Map<String, Object> location = new LinkedHashMap<>();
        List<ProjectDirectory> directories = projectDirectories;
        if (directories == null) {
            // Input snapshots are captured before the first task record is written
            ensureProjectsRecordWritten();
            directories = projectDirectories;
        }
        if (directories != null) {
            for (ProjectDirectory directory : directories) {
                if (absolutePath.equals(directory.absoluteDir)) {
                    location.put("project", directory.identityPath);
                    location.put("path", "");
                    return location;
                }
                if (absolutePath.startsWith(directory.absoluteDir + File.separator)) {
                    location.put("project", directory.identityPath);
                    location.put("path", relativeTo(directory.absoluteDir, absolutePath));
                    return location;
                }
            }
        }
        location.put("path", absolutePath);
        return location;
    }

    private static String relativeTo(String root, String path) {
        if (path.equals(root)) {
            return "";
        }
        String relative = path.startsWith(root + File.separator) ? path.substring(root.length() + 1) : path;
        return relative.replace(File.separatorChar, '/');
    }

    // Identities and helpers

    /**
     * Tasks are identified by their build tree path (Gradle's identity path), e.g. {@code :app:compileJava}
     * in the root build and {@code :included:lib:jar} in an included build.
     */
    private static List<String> nodeTaskPaths(Collection<Node> nodes) {
        List<String> paths = new ArrayList<>();
        for (Node node : nodes) {
            if (node instanceof TaskInAnotherBuild) {
                paths.add(((TaskInAnotherBuild) node).getTaskIdentityPath().asString());
            } else if (node instanceof TaskNode) {
                paths.add(((TaskNode) node).getTask().getIdentityPath().asString());
            }
        }
        Collections.sort(paths);
        return paths;
    }

    private static List<String> buildTreeTaskPathsOf(List<CalculateTaskGraphBuildOperationType.TaskIdentity> identities) {
        List<String> paths = new ArrayList<>();
        for (CalculateTaskGraphBuildOperationType.TaskIdentity identity : identities) {
            paths.add(buildTreeTaskPathOf(identity));
        }
        Collections.sort(paths);
        return paths;
    }

    private static List<String> buildTreeTaskPathsOf(String buildPath, List<String> taskPaths) {
        List<String> paths = new ArrayList<>();
        for (String taskPath : taskPaths) {
            paths.add(buildTreeTaskPathOf(buildPath, taskPath));
        }
        return paths;
    }

    private static String buildTreeTaskPathOf(CalculateTaskGraphBuildOperationType.TaskIdentity identity) {
        return buildTreeTaskPathOf(identity.getBuildPath(), identity.getTaskPath());
    }

    /**
     * The root build's path {@code :} collapses, so {@code :} + {@code :app:compileJava} is {@code :app:compileJava}.
     */
    private static String buildTreeTaskPathOf(String buildPath, String taskPath) {
        return Path.path(buildPath).append(Path.path(taskPath)).asString();
    }

    private static @Nullable String hex(byte @Nullable [] bytes) {
        return bytes == null ? null : HashCode.fromBytes(bytes).toString();
    }

    private static <T> T requireNonNull(@Nullable T value) {
        if (value == null) {
            throw new IllegalStateException("Visitor called out of order");
        }
        return value;
    }

    private void emit(Map<String, Object> record) {
        workSink.accept(JsonOutput.toJson(record));
    }
}
