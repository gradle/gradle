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

package org.gradle.api.tasks.javadoc

import org.gradle.api.tasks.util.ExistingDirsFilter
import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.Task
import org.gradle.api.internal.ConventionTask

/**
 * @author Hans Dockter
 */
class Groovydoc extends ConventionTask {
    List srcDirs

    File destDir

    ExistingDirsFilter existentDirsFilter = new ExistingDirsFilter()

    AntGroovydoc antGroovydoc = new AntGroovydoc()

    def self

    Groovydoc(DefaultProject project, String name) {
        super(project, name)
        actions << this.&generate
        self = this
    }

    private void generate(Task task) {
        List existingSourceDirs = existentDirsFilter.checkDestDirAndFindExistingDirsAndThrowStopActionIfNone(
                self.destDir, self.srcDirs)
        antGroovydoc.execute(existingSourceDirs, self.destDir, project.ant)
    }

}
