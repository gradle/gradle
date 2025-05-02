/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.tooling.internal.provider.continuous;

import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.watch.registry.FileWatcherRegistry;
import org.gradle.internal.watch.vfs.FileChangeListener;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class FileEventCollector implements FileChangeListener {
    public static final int SHOW_INDIVIDUAL_CHANGES_LIMIT = 3;

    private final Map<Path, FileWatcherRegistry.Type> aggregatedEvents = new LinkedHashMap<>();
    private final BuildInputHierarchy buildInputs;
    private final Runnable onRelevantChangeAction;
    private int moreChangesCount;
    private boolean errorWhenWatching;

    public FileEventCollector(BuildInputHierarchy buildInputs, Runnable onRelevantChangeAction) {
        this.buildInputs = buildInputs;
        this.onRelevantChangeAction = onRelevantChangeAction;
    }

    @Override
    public void handleChange(FileWatcherRegistry.Type type, Path path) {
        String absolutePath = path.toString();
        if (buildInputs.isInput(absolutePath)) {
            // got a change, store it
            onChangeToInputs(type, path);
            onRelevantChangeAction.run();
        }
    }

    @Override
    public void stopWatchingAfterError() {
        errorWhenWatching = true;
        onRelevantChangeAction.run();
    }

    public void onChangeToInputs(FileWatcherRegistry.Type type, Path path) {
        FileWatcherRegistry.Type existingEvent = aggregatedEvents.get(path);
        if (existingEvent == type ||
            (existingEvent == FileWatcherRegistry.Type.CREATED && type == FileWatcherRegistry.Type.MODIFIED)) {
            return;
        }

        if (existingEvent != null || aggregatedEvents.size() < SHOW_INDIVIDUAL_CHANGES_LIMIT) {
            aggregatedEvents.put(path, type);
        } else {
            moreChangesCount++;
        }
    }

    public void reportChanges(StyledTextOutput logger) {
        for (Map.Entry<Path, FileWatcherRegistry.Type> entry : aggregatedEvents.entrySet()) {
            FileWatcherRegistry.Type type = entry.getValue();
            Path path = entry.getKey();
            showIndividualChange(logger, path, type);
        }
        if (moreChangesCount > 0) {
            logOutput(logger, "and some more changes");
        }
        if (errorWhenWatching) {
            logOutput(logger, "Error when watching files - triggering a rebuild");
        }
    }

    private void showIndividualChange(StyledTextOutput logger, Path path, FileWatcherRegistry.Type changeType) {
        String changeDescription;
        switch (changeType) {
            case CREATED:
                changeDescription = "new " + (Files.isDirectory(path) ? "directory" : "file");
                break;
            case REMOVED:
                changeDescription = "deleted";
                break;
            case MODIFIED:
            default:
                changeDescription = "modified";
        }
        logOutput(logger, "%s: %s", changeDescription, path.toString());
    }

    private void logOutput(StyledTextOutput logger, String message, Object... objects) {
        logger.formatln(message, objects);
    }
}
