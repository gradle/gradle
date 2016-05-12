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

package org.gradle.api.internal.changedetection.state;

import org.gradle.BuildListener;
import org.gradle.BuildResult;
import org.gradle.api.Task;
import org.gradle.api.Transformer;
import org.gradle.api.execution.TaskExecutionGraph;
import org.gradle.api.execution.TaskExecutionGraphListener;
import org.gradle.api.execution.TaskExecutionListener;
import org.gradle.api.file.FileCollection;
import org.gradle.api.initialization.Settings;
import org.gradle.api.internal.file.BackingFileExtractor;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.TaskState;
import org.gradle.internal.Factory;
import org.gradle.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

class TreeVisitorCacheExpirationStrategy implements TaskExecutionGraphListener, TaskExecutionListener, BuildListener {
    private final static Logger LOG = Logging.getLogger(TreeVisitorCacheExpirationStrategy.class);
    private final CachingTreeVisitor cachingTreeVisitor;
    private final Factory<OverlappingDirectoriesDetector> overlappingDirectoriesDetectorFactory;
    // used to flush cache for a file path after the last task
    private Map<String, Collection<String>> lastTaskToHandleInputFile;
    private Set<String> tasksWithUnknownInputs;
    private Set<String> tasksWithUnknownOutputs;

    public TreeVisitorCacheExpirationStrategy(CachingTreeVisitor cachingTreeVisitor, Factory<OverlappingDirectoriesDetector> overlappingDirectoriesDetectorFactory) {
        this.cachingTreeVisitor = cachingTreeVisitor;
        this.overlappingDirectoriesDetectorFactory = overlappingDirectoriesDetectorFactory;
    }

    @Override
    public void graphPopulated(TaskExecutionGraph graph) {
        resolveCacheableFilesAndLastTasksToHandleEachFile(graph);
    }

    private void resolveCacheableFilesAndLastTasksToHandleEachFile(TaskExecutionGraph graph) {
        // any output used by an downstream task is cacheable
        // any input used by 2 tasks is cacheable
        List<String> cacheableFilePaths = new ArrayList<String>();

        tasksWithUnknownInputs = new HashSet<String>();
        tasksWithUnknownOutputs = new HashSet<String>();

        Map<String, List<String>> inputFileToTaskPaths = new HashMap<String, List<String>>();

        Set<String> cumulatedInputsAndOutputs = new HashSet<String>();

        OverlappingDirectoriesDetector detector = overlappingDirectoriesDetectorFactory.create();
        for (Task task : graph.getAllTasks()) {
            final String taskPath = task.getPath();
            List<String> inputPaths = extractFilePaths(task.getInputs().getFiles());
            if (inputPaths.size() > 0) {
                storeFilesToMapWithTaskPathAsKey(taskPath, inputPaths, inputFileToTaskPaths);
                for (String path : inputPaths) {
                    if (cumulatedInputsAndOutputs.contains(path)) {
                        cacheableFilePaths.add(path);
                    }
                }
                cumulatedInputsAndOutputs.addAll(inputPaths);
            } else if (!task.getInputs().getHasInputs()) {
                tasksWithUnknownInputs.add(taskPath);
            }

            List<String> outputPaths = extractFilePaths(task.getOutputs().getFiles());
            if (outputPaths.size() > 0) {
                cumulatedInputsAndOutputs.addAll(outputPaths);
                detector.addPaths(outputPaths);
            } else if (!task.getOutputs().getHasOutput()) {
                tasksWithUnknownOutputs.add(taskPath);
            }
        }

        resolveLastTasksToHandleEachFile(inputFileToTaskPaths);

        cacheableFilePaths.removeAll(detector.resolveOverlappingPaths());
        cachingTreeVisitor.updateCacheableFilePaths(cacheableFilePaths);
    }

    private void resolveLastTasksToHandleEachFile(Map<String, List<String>> inputFileToTaskPaths) {
        lastTaskToHandleInputFile = new HashMap<String, Collection<String>>();
        for (Map.Entry<String, List<String>> entry : inputFileToTaskPaths.entrySet()) {
            String lastTaskPath = entry.getValue().get(entry.getValue().size() - 1);
            Collection<String> filePaths = lastTaskToHandleInputFile.get(lastTaskPath);
            if (filePaths == null) {
                filePaths = new ArrayList<String>();
                lastTaskToHandleInputFile.put(lastTaskPath, filePaths);
            }
            filePaths.add(entry.getKey());
        }
    }

    private void storeFilesToMapWithTaskPathAsKey(String taskPath, List<String> files, Map<String, List<String>> map) {
        for (String filePath : files) {
            List<String> currentTaskPaths = map.get(filePath);
            if (currentTaskPaths == null) {
                currentTaskPaths = new ArrayList<String>();
                map.put(filePath, currentTaskPaths);
            }
            currentTaskPaths.add(taskPath);
        }
    }

    private List<String> extractFilePaths(FileCollection files) {
        List<BackingFileExtractor.FileEntry> entries = new BackingFileExtractor().extractFilesOrDirectories(files);
        return CollectionUtils.collect(entries, new Transformer<String, BackingFileExtractor.FileEntry>() {
            @Override
            public String transform(BackingFileExtractor.FileEntry fileEntry) {
                return fileEntry.getFile().getAbsolutePath();
            }
        });
    }

    @Override
    public void beforeExecute(Task task) {
        final String taskPath = task.getPath();
        if (tasksWithUnknownInputs != null && tasksWithUnknownInputs.contains(taskPath)) {
            LOG.info("Flushing directory cache because task {} has unknown inputs at configuration time.", taskPath);
            cachingTreeVisitor.clearCache();
        }
    }

    @Override
    public void afterExecute(Task task, TaskState state) {
        final String taskPath = task.getPath();
        if (tasksWithUnknownOutputs != null && tasksWithUnknownOutputs.contains(taskPath)) {
            LOG.info("Flushing directory cache because task {} has unknown outputs at configuration time.", taskPath);
            cachingTreeVisitor.clearCache();
        } else if (lastTaskToHandleInputFile != null) {
            Collection<String> filePaths = lastTaskToHandleInputFile.get(taskPath);
            if (filePaths != null) {
                cachingTreeVisitor.invalidateFilePaths(filePaths);
            }
        }
    }

    @Override
    public void buildStarted(Gradle gradle) {

    }

    @Override
    public void settingsEvaluated(Settings settings) {

    }

    @Override
    public void projectsLoaded(Gradle gradle) {

    }

    @Override
    public void projectsEvaluated(Gradle gradle) {

    }

    @Override
    public void buildFinished(BuildResult result) {
        cachingTreeVisitor.clearCache();
    }
}
