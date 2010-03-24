/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.ConventionTask;
import org.gradle.util.GFileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * <p>Deletes the specified target files or directories.</p>
 *
 * @author Hans Dockter
 */
public class Delete extends ConventionTask {
    private static Logger logger = LoggerFactory.getLogger(Delete.class);

    private Set<Object> delete = new LinkedHashSet<Object>();

    @TaskAction
    protected void clean() {
        setDidWork(false);

        for (File file : getTargetFiles()) {
            if (!file.exists()) {
                continue;
            }
            logger.debug("Deleting {}", file);
            setDidWork(true);
            if (file.isFile()) {
                GFileUtils.deleteQuietly(file);
            } else {
                GFileUtils.deleteDirectory(file);
            }
        }
    }

    /**
     * Returns the resolved set of files which will be deleted by this task.
     *
     * @return The files. Never returns null.
     */
    public FileCollection getTargetFiles() {
        return getProject().files(getDelete());
    }

    /**
     * Returns the set of files which will be deleted by this task.
     *
     * @return The files. Never returns null.
     */
    public Set<Object> getDelete() {
        return delete;
    }

    /**
     * Sets the files to be deleted by this task.
     *
     * @param target Any type of object accepted by {@link org.gradle.api.Project#files(Object...)}
     */
    public void setDelete(Object target) {
        delete.clear();
        this.delete.add(target);
    }

    /**
     * Adds some files to be deleted by this task.
     *
     * @param targets Any type of object accepted by {@link org.gradle.api.Project#files(Object...)}
     */
    public Delete delete(Object... targets) {
        for (Object target : targets) {
            this.delete.add(target);
        }
        return this;
    }
}
