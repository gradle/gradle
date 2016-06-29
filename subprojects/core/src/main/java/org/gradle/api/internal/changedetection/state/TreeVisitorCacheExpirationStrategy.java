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

import org.gradle.BuildAdapter;
import org.gradle.BuildResult;
import org.gradle.api.Task;
import org.gradle.api.Transformer;
import org.gradle.api.execution.TaskExecutionGraph;
import org.gradle.api.execution.TaskExecutionGraphListener;
import org.gradle.api.execution.TaskExecutionListener;
import org.gradle.api.file.FileCollection;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.TaskState;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.event.ListenerManager;
import org.gradle.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.gradle.api.internal.changedetection.state.CachingTreeVisitor.CACHING_TREE_VISITOR_FEATURE_ENABLED;

/*
 This class determines what task input file paths are cacheable by the CachingTreeVisitor ("tree snapshot cache")
 used in up-to-date checking to reduce unnecessary directory scanning.

 - extract inputs/outputs and determine what are cacheable when the TaskExecutionGraph gets populated
   - only cache those that are used multiple times
      - same directory input for multiple tasks
      - another downstream task uses the output of some task
   - overlapping output directories aren't cacheable at all

 This class also handles cache invalidation:
 - if a task has unknown inputs, flush cache before the task is executed
 - if a task has unknown outputs, flush cache after the task is executed
 - invalidate entry from cache after the last task using the file path as input has been executed
 - invalidate all entries in the cache when the build finishes
*/
public class TreeVisitorCacheExpirationStrategy implements Stoppable {
    private final static Logger LOG = Logging.getLogger(TreeVisitorCacheExpirationStrategy.class);
    private final CachingTreeVisitor cachingTreeVisitor;
    private final ListenerManager listenerManager;
    // used to flush cache for a file path after the last task
    private Map<String, Collection<String>> lastTaskToHandleInputFile;
    private Set<String> tasksWithUnknownInputs;
    private Set<String> tasksWithUnknownOutputs;
    private final TreeVisitorCacheTaskExecutionListener taskExecutionListener = new TreeVisitorCacheTaskExecutionListener();
    private final TreeVisitorCacheBuildAdapter buildListener = new TreeVisitorCacheBuildAdapter();
    private final TreeVisitorCacheTaskExecutionGraphListener taskExecutionGraphListener = new TreeVisitorCacheTaskExecutionGraphListener();
    private final boolean swallowExceptions;

    public TreeVisitorCacheExpirationStrategy(CachingTreeVisitor cachingTreeVisitor, ListenerManager listenerManager) {
        this(cachingTreeVisitor, listenerManager, true);
    }

    public TreeVisitorCacheExpirationStrategy(CachingTreeVisitor cachingTreeVisitor, ListenerManager listenerManager, boolean swallowExceptions) {
        this.cachingTreeVisitor = cachingTreeVisitor;
        this.listenerManager = listenerManager;
        this.swallowExceptions = swallowExceptions;
        registerListeners();
    }

    private void registerListeners() {
        if (CACHING_TREE_VISITOR_FEATURE_ENABLED) {
            listenerManager.addListener(buildListener);
            listenerManager.addListener(taskExecutionGraphListener);
            listenerManager.addListener(taskExecutionListener);
        }
    }

    private void removeListeners() {
        if (CACHING_TREE_VISITOR_FEATURE_ENABLED) {
            listenerManager.removeListener(taskExecutionListener);
            listenerManager.removeListener(taskExecutionGraphListener);
            listenerManager.removeListener(buildListener);
        }
    }

    public void stop() {
        removeListeners();
    }

    private void clearCache() {
        cachingTreeVisitor.clearCache();
        cachingTreeVisitor.updateCacheableFilePaths(null);
        lastTaskToHandleInputFile = null;
        tasksWithUnknownOutputs = null;
        tasksWithUnknownInputs = null;
    }

    private void resolveCacheableFilesAndLastTasksToHandleEachFile(TaskExecutionGraph graph) {
        List<String> cacheableFilePaths = new ArrayList<String>();

        tasksWithUnknownInputs = new HashSet<String>();
        tasksWithUnknownOutputs = new HashSet<String>();

        Map<String, List<String>> inputFileToTaskPaths = new HashMap<String, List<String>>();

        Set<String> cumulatedInputsAndOutputs = new HashSet<String>();

        OverlappingDirectoriesDetector detector = new OverlappingDirectoriesDetector();
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
            } else if (!task.getInputs().getHasInputs() && !task.getActions().isEmpty()) {
                tasksWithUnknownInputs.add(taskPath);
            }

            List<String> outputPaths = extractFilePaths(task.getOutputs().getFiles());
            if (outputPaths.size() > 0) {
                cumulatedInputsAndOutputs.addAll(outputPaths);
                detector.addPaths(outputPaths);
            } else if (!task.getOutputs().getHasOutput() && !task.getActions().isEmpty()) {
                tasksWithUnknownOutputs.add(taskPath);
            }
        }

        cacheableFilePaths.removeAll(detector.resolveOverlappingPaths());

        resolveLastTasksToHandleEachFile(inputFileToTaskPaths, new HashSet<String>(cacheableFilePaths));

        cachingTreeVisitor.updateCacheableFilePaths(cacheableFilePaths);
    }

    private void resolveLastTasksToHandleEachFile(Map<String, List<String>> inputFileToTaskPaths, Collection<String> cacheableFilePaths) {
        lastTaskToHandleInputFile = new HashMap<String, Collection<String>>();
        for (Map.Entry<String, List<String>> entry : inputFileToTaskPaths.entrySet()) {
            String filePath = entry.getKey();
            if (cacheableFilePaths.contains(filePath)) {
                String lastTaskPath = entry.getValue().get(entry.getValue().size() - 1);
                Collection<String> filePaths = lastTaskToHandleInputFile.get(lastTaskPath);
                if (filePaths == null) {
                    filePaths = new ArrayList<String>();
                    lastTaskToHandleInputFile.put(lastTaskPath, filePaths);
                }
                filePaths.add(filePath);
            }
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

    private class TreeVisitorCacheTaskExecutionGraphListener implements TaskExecutionGraphListener {
        @Override
        public void graphPopulated(TaskExecutionGraph graph) {
            try {
                resolveCacheableFilesAndLastTasksToHandleEachFile(graph);
            } catch (RuntimeException e) {
                LOG.info("Exception '{}' while resolving task inputs and outputs. Disabling tree visitor caching for this build.", e.getMessage());
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Exception details", e);
                }
                clearCache();
                if (!swallowExceptions) {
                    throw e;
                }
            }
        }
    }

    private class TreeVisitorCacheTaskExecutionListener implements TaskExecutionListener {
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
    }

    private class TreeVisitorCacheBuildAdapter extends BuildAdapter {
        @Override
        public void buildFinished(BuildResult result) {
            clearCache();
        }
    }
}
