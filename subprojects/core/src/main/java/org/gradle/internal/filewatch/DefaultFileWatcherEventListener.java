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
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.logging.text.StyledTextOutput;

import java.io.File;
import java.util.Map;

import static org.gradle.internal.filewatch.FileWatcherEvent.Type.*;

public class DefaultFileWatcherEventListener implements FileWatcherEventListener {
    public static final int SHOW_INDIVIDUAL_CHANGES_LIMIT = 3;
    private static final boolean IS_MAC_OSX = OperatingSystem.current().isMacOsX();
    private final Map<File, FileWatcherEvent.Type> aggregatedEvents = Maps.newLinkedHashMap();
    private int moreChangesCount;

    private void logOutput(StyledTextOutput logger, String message, Object... objects) {
        logger.formatln(message, objects);
    }

    @Override
    public void onChange(FileWatcherEvent event) {
        if (event.getType() == UNDEFINED) {
            return;
        }

        File file = event.getFile();
        FileWatcherEvent.Type existingType = aggregatedEvents.get(file);

        if (existingType == event.getType()
            || existingType == CREATE && event.getType() == MODIFY) {
            return;
        }

        if (existingType != null || aggregatedEvents.size() < SHOW_INDIVIDUAL_CHANGES_LIMIT) {
            aggregatedEvents.put(file, event.getType());
        } else if (shouldIncreaseChangesCount(event)) {
            moreChangesCount++;
        }
    }

    protected boolean shouldIncreaseChangesCount(FileWatcherEvent event) {
        if (IS_MAC_OSX) {
            return true; // count every event on OSX
        }

        // Only count non-CREATE events, since creation also causes a modification event, unless the event is for a directory.
        return event.getType() != CREATE || event.getFile().isDirectory();
    }

    public void reportChanges(StyledTextOutput logger) {
        for (Map.Entry<File, FileWatcherEvent.Type> entry : aggregatedEvents.entrySet()) {
            FileWatcherEvent.Type changeType = entry.getValue();
            File file = entry.getKey();
            showIndividualChange(logger, file, changeType);
        }
        if (moreChangesCount > 0) {
            logOutput(logger, "and some more changes");
        }
    }

    private void showIndividualChange(StyledTextOutput logger, File file, FileWatcherEvent.Type changeType) {
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
        logOutput(logger, "%s: %s", changeDescription, file.getAbsolutePath());
    }
}
