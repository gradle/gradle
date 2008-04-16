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
 * Copies the content of the <code>sourceDirs</code> to the <code>destinationDir</code>.
 * You can define global or local includes, excludes or filters. Global ones apply to all
 * source dirs local ones only to a single source dir.
 *
 * @author Hans Dockter
 */
class Resources extends ConventionTask {
    private static Logger logger = LoggerFactory.getLogger(Resources)

    protected Resources self

    /**
     * A list of file objects denoting the directories to extract the content from.
     */
    List sourceDirs = []

    /**
     * The directory where to copy then content from the source dirs.
     */
    File destinationDir

    /**
     * A set of include pattern (e.g. <code>'**//*.txt'</code>) which is applied to all source dirs.
     * The syntax of the include patterns is the same as the ant syntax for include patterns.
     */
    Set globalIncludes = []

    /**
     * A set of exclude pattern (e.g. <code>'**//*.txt'</code>) which is applied to all source dirs.
     * The syntax of the exclude patterns is the same as the ant syntax for exclude patterns.
     */
    Set globalExcludes = []

    /**
     * A map of filters which is applied to all source dirs. An example is: <code>[TODAY: new Date()]</code>,
     * which would replace the text content <code>@TODAY@</code> with the current date.
     * We use an Ant filterset to implement this. Right now you can't specify the token specifier, it's always @.
     */
    Map globalFilters = [:]

    /**
     * A set of include pattern (e.g. <code>'**//*.txt'</code>) which is applied to all source dirs.
     * The key of the map is the file object denoting the source dir. The value of the entry is a set of include
     * patterns.
     * The syntax of the include patterns is the same as the ant syntax for include patterns.
     */
    Map sourceDirIncludes = [:]

    /**
     * A map of exclude pattern (e.g. <code>'**//*.txt'</code>) which is applied to a single source dirs.
     * The key of the map is the file object denoting the source dir. The value of the entry is a set of exclude
     * patterns.
     * The syntax of the exclude patterns is the same as the ant syntax for exclude patterns.
     */
    Map sourceDirExcludes = [:]

    /**
     * A map of filters which is applied to a single source dirs. An example is: <code>[TODAY: new Date()]</code>,
     * which would replace the text content <code>@TODAY@</code> with the current date.
     * The key of the map is the file object denoting the source dir. The value of the entry is a map of filters.
     * We use an Ant filterset to implement this. Right now you can't specify the token specifier, it's always @.
     */
    Map sourceDirFilters = [:]

    // It is too cumbersone, if every user of this class has to set this property.
    // So we break IoC a little bit, but anyone can inject a new instance. And this is Groovy,
    // so we don't need an interface.

    protected CopyInstructionFactory copyInstructionFactory = new CopyInstructionFactory()

    protected ExistingDirsFilter existentDirsFilter = new ExistingDirsFilter()

    Resources(Project project, String name) {
        super(project, name)
        doLast(this.&copyResources)
        self = this
    }

    private void copyResources(Task task) {
        List existingSourceDirs = existentDirsFilter.checkDestDirAndFindExistingDirsAndThrowStopActionIfNone(
                self.destinationDir, self.sourceDirs)

        List copyInstructions = existingSourceDirs.collect {File sourceDir ->
            Set includes = getSetFromMap(sourceDirIncludes, sourceDir) + globalIncludes
            Set excludes = getSetFromMap(sourceDirExcludes, sourceDir) + globalExcludes
            Map filters = getMapFromMap(sourceDirFilters, sourceDir) + globalFilters
            copyInstructionFactory.createCopyInstruction(sourceDir, self.destinationDir, includes, excludes, filters)
        }
        copyInstructions*.execute()
    }

    /**
     * adds the given sourceDirs to the sourceDirs property.
     */
    Resources from(File[] sourceDirs) {
        this.sourceDirs.addAll(sourceDirs as List)
        this
    }

    /**
     * sets the destination dir (equivalent to <code>resources.destinationDir = </code>
     */
    Resources into(File destinationDir) {
        this.destinationDir = destinationDir
        this
    }

    /**
     * Add includes patterns. If the sourceDir is specified the pattern is limited to the specified source dir.
     * Otherwise it is a global pattern applied to all source dirs.
     */
    Resources includes(File sourceDir = null, String[] includes) {
        addIncludesExcludes(this.sourceDirIncludes, globalIncludes, sourceDir, includes)
    }

    /**
     * Add excludes patterns. If the sourceDir is specified the pattern is limited to the specified source dir.
     * Otherwise it is a global pattern applied to all source dirs.
     */
    Resources excludes(File sourceDir = null, String[] excludes) {
        addIncludesExcludes(this.sourceDirExcludes, globalExcludes, sourceDir, excludes)
    }

    /**
     * Add filters. If the sourceDir is specified the filters are limited to the specified source dir.
     * Otherwise they are global filters applied to all source dirs.
     */
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