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

package org.gradle.api.tasks;

import org.apache.commons.io.FileUtils;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.TaskAction;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.tasks.util.ExistingDirsFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

/**
* @author Hans Dockter
*/
public class Clean extends ConventionTask {
    private static Logger logger = LoggerFactory.getLogger(Clean.class);

    private File dir;

    private ExistingDirsFilter existingDirsFilter = new ExistingDirsFilter();

    public Clean(Project project, String name) {
        super(project, name);
        doFirst(new TaskAction() {
            public void execute(Task task) {
                clean();
            }
        });
    }

    private void clean() {
        if (getDir() == null) {
            throw new InvalidUserDataException("The dir property must be specified!");
        }
        existingDirsFilter.checkExistenceAndThrowStopActionIfNot(getDir());
        logger.debug("Deleting dir: {}", getDir());

        try {
            FileUtils.deleteDirectory(getDir());
        } catch (IOException e) {
            throw new InvalidUserDataException("Can't delete directory: " + getDir());
        }
    }

    public File getDir() {
        return dir;
    }

    public void setDir(File dir) {
        this.dir = dir;
    }

    public ExistingDirsFilter getExistingDirsFilter() {
        return existingDirsFilter;
    }

    public void setExistingDirsFilter(ExistingDirsFilter existingDirsFilter) {
        this.existingDirsFilter = existingDirsFilter;
    }
}
