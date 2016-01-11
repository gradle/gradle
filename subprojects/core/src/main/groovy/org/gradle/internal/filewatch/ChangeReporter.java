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

package org.gradle.internal.filewatch;

import com.google.common.collect.Maps;
import org.gradle.logging.StyledTextOutput;

import java.io.File;
import java.util.List;
import java.util.Map;

public class ChangeReporter {
    public static final int SHOW_INDIVIDUAL_CHANGES_LIMIT = 3;
    private final StyledTextOutput logger;

    public ChangeReporter(StyledTextOutput logger) {
        this.logger = logger;
    }

    private void logOutput(String message, Object... objects) {
        logger.formatln(message, objects);
    }

    public void reportChanges(List<FileWatcherEvent> fileWatchEvents) {
        Map<File, FileWatcherEvent.Type> aggregatedEvents = aggregateEvents(fileWatchEvents);
        int counter = 0;
        for (Map.Entry<File, FileWatcherEvent.Type> entry : aggregatedEvents.entrySet()) {
            FileWatcherEvent.Type changeType = entry.getValue();
            File file = entry.getKey();
            counter++;
            if (counter <= SHOW_INDIVIDUAL_CHANGES_LIMIT) {
                showIndividualChange(file, changeType);
            } else {
                int moreChanges = aggregatedEvents.size() - SHOW_INDIVIDUAL_CHANGES_LIMIT;
                logOutput("and %d more changes", moreChanges);
                break;
            }
        }
    }

    private void showIndividualChange(File file, FileWatcherEvent.Type changeType) {
        String changeDescription;
        switch (changeType) {
            case CREATE:
                changeDescription = "new " + (file.isDirectory() ? "directory" : "file");
                break;
            case DELETE:
                changeDescription = "deleted";
                break;
            case MODIFY:
            default:
                changeDescription = "modified";
        }
        logOutput("%s: %s", changeDescription, file.getAbsolutePath());
    }

    private Map<File, FileWatcherEvent.Type> aggregateEvents(List<FileWatcherEvent> fileWatchEvents) {
        Map<File, FileWatcherEvent.Type> aggregatedEvents = Maps.newLinkedHashMap();
        for (FileWatcherEvent event : fileWatchEvents) {
            if (event.getType() == FileWatcherEvent.Type.UNDEFINED) {
                continue;
            }

            File file = event.getFile();
            FileWatcherEvent.Type existingType = aggregatedEvents.get(file);

            if (existingType == event.getType()
                || existingType == FileWatcherEvent.Type.CREATE && event.getType() == FileWatcherEvent.Type.MODIFY) {
                continue;
            }

            aggregatedEvents.put(file, event.getType());
        }
        return aggregatedEvents;
    }
}
