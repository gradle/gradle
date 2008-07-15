/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.api.tasks.util;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.tasks.StopActionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.io.File;

/**
 * @author Hans Dockter
 */
public class ExistingDirsFilter {
    private static Logger logger = LoggerFactory.getLogger(ExistingDirsFilter.class);

    public List<File> findExistingDirs(Collection<File> dirFiles) {
        List<File> existingDirs = new ArrayList<File>();
        for (File dirFile : dirFiles) {
            if (dirFile.isDirectory()) {
                existingDirs.add(dirFile);
            }
        }
        return existingDirs;
    }

    public List<File> findExistingDirsAndLogExitMessage(Collection dirFiles) {
        logger.debug("Looking for existing folders: {}", dirFiles);
        List result = findExistingDirs(dirFiles);
        if (result.size() == 0) {
            logger.debug("No existing directories to work on. We don't do anything here.");
        }
        return result;
    }

    public List<File> findExistingDirsAndThrowStopActionIfNone(Collection dirFiles) {
        List result = findExistingDirsAndLogExitMessage(dirFiles);
        if (result.size() == 0) {
            throw new StopActionException();
        }
        return result;
    }

    public List<File> checkDestDirAndFindExistingDirsAndThrowStopActionIfNone(File destDir, Collection dirFiles) {
        if (destDir == null) {
            throw new InvalidUserDataException("The destination dir is not set!");
        }
        return findExistingDirsAndThrowStopActionIfNone(dirFiles);
    }

    public void checkExistenceAndThrowStopActionIfNot(File dir) {
        findExistingDirsAndThrowStopActionIfNone(Collections.singletonList(dir));
    }
}
