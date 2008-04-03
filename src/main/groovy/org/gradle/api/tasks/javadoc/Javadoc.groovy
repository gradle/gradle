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

import org.gradle.api.internal.ConventionTask
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.Task
import org.gradle.api.tasks.util.ExistingDirsFilter

/**
 * @author Hans Dockter
 */
class Javadoc extends ConventionTask {
    private static Logger logger = LoggerFactory.getLogger(Javadoc)

    List srcDirs

    File destDir

    String maxMemory

    ExistingDirsFilter existentDirsFilter = new ExistingDirsFilter()

    AntJavadoc antJavadoc = new AntJavadoc()

    def self

    Javadoc(DefaultProject project, String name) {
        super(project, name)
        actions << this.&generate
        self = this
    }

    private void generate(Task task) {
        List existingSourceDirs = existentDirsFilter.checkDestDirAndFindExistingDirsAndThrowStopActionIfNone(self.destDir, self.setSrcDirs)
        antJavadoc.execute(existingSourceDirs, self.destDir, self.maxMemory, project.ant)   
    }

}
