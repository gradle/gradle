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

package org.gradle.api.tasks

import org.gradle.api.InvalidUserDataException
import org.gradle.api.Task
import org.gradle.api.internal.ConventionTask
import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.tasks.util.ExistingDirsFilter
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
* @author Hans Dockter
*/
class Clean extends ConventionTask {
    private static Logger logger = LoggerFactory.getLogger(Clean)

    File dir

    ExistingDirsFilter existingDirsFilter = new ExistingDirsFilter()

    Clean self

    Clean(DefaultProject project, String name) {
        super(project, name)
        actions << this.&clean
        self = this
    }

    private void clean(Task task) {
        if (self.dir == null) {
            throw new InvalidUserDataException("The dir property must be specified!")
        }
        if (!existingDirsFilter.checkExistenceAndLogExitMessage(self.dir)) {return}
        logger.debug("Deleting dir: $self.dir")
        project.ant.delete(dir: self.dir)
    }

}