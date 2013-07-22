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

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * <p>Deletes files or directories. Example:</p>
 * <pre autoTested=''>
 * task makePretty(type: Delete) {
 *   delete 'uglyFolder', 'uglyFile'
 * }
 * </pre>
 */
public class Delete extends ConventionTask {
    private Set<Object> delete = new LinkedHashSet<Object>();

    @TaskAction
    protected void clean() {
        setDidWork(getProject().delete(delete));
    }

    /**
     * Returns the resolved set of files which will be deleted by this task.
     *
     * @return The files. Never returns null.
     */
    public FileCollection getTargetFiles() {
        return getProject().files(delete);
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
     * Adds some files to be deleted by this task. The given targets are evaluated as per {@link org.gradle.api.Project#files(Object...)}.
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
