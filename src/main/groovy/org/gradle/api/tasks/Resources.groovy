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

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.internal.ConventionTask
import org.gradle.api.tasks.util.CopyInstructionFactory
import org.gradle.api.tasks.util.ExistingDirsFilter
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
* @author Hans Dockter
*/
// todo: rename targetDir to destinationDir
class Resources extends ConventionTask {
    private static Logger logger = LoggerFactory.getLogger(Resources)

    Resources self

    List sourceDirs = []

    File targetDir

    Set globalIncludes = []
    Set globalExcludes = []
    Map globalFilters = [:]

    Map sourceDirIncludes = [:]
    Map sourceDirExcludes = [:]
    Map sourceDirFilters = [:]

    // It is too cumbersone, if every user of this class has to set this property.
    // So we break IoC a little bit, but anyone can inject a new instance. And this is Groovy,
    // so we don't need an interface.
    ExistingDirsFilter existentDirsFilter = new ExistingDirsFilter()
    
    CopyInstructionFactory copyInstructionFactory = new CopyInstructionFactory()

    Resources(Project project, String name) {
        super(project, name)
        doLast(this.&copyResources)
        self = this
    }

    private void copyResources(Task task) {
        List existingSourceDirs = existentDirsFilter.checkDestDirAndFindExistingDirsAndThrowStopActionIfNone(
                self.targetDir, self.sourceDirs)
        
        List copyInstructions = existingSourceDirs.collect { File sourceDir ->
            Set includes = getSetFromMap(sourceDirIncludes, sourceDir) + globalIncludes
            Set excludes = getSetFromMap(sourceDirExcludes, sourceDir) + globalExcludes
            Map filters = getMapFromMap(sourceDirFilters, sourceDir) + globalFilters
            copyInstructionFactory.createCopyInstruction(sourceDir, self.targetDir, includes, excludes, filters)
        }
        copyInstructions*.execute()
    }


    Resources from(File[] sourceDirs) {
        this.sourceDirs.addAll(sourceDirs as List)
        this
    }

    Resources into(File targetDir) {
        this.targetDir = targetDir
        this
    }

    Resources includes(File sourceDir = null, String[] includes) {
        addIncludesExcludes(this.sourceDirIncludes, globalIncludes, sourceDir, includes)
    }

    Resources excludes(File sourceDir = null, String[] excludes) {
        addIncludesExcludes(this.sourceDirExcludes, globalExcludes, sourceDir, excludes)
    }

    Resources filter(File sourceDir = null, Map filters) {
        Map mapToAddTo = sourceDir ? getMapFromMap(sourceDirFilters, sourceDir) : globalFilters
        mapToAddTo.putAll(filters)
        this
    }

    private Map getMapFromMap(Map map, def key) {
        if (!map[key]) {
            map[key] = [:]
        }
        map[key]
    } 

    private Resources addIncludesExcludes(Map map, Set globals, File sourceDir, String[] args) {
        Set listToAddTo = sourceDir ? getSetFromMap(map, sourceDir) : globals
        listToAddTo.addAll(args as List)
        this
    }

    private Set getSetFromMap(Map map, def key) {
        if (!map[key]) { 
            map[key] = [] as HashSet
        }
        map[key]
    }
}