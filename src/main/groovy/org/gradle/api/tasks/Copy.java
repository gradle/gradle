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

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.TaskAction;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.tasks.util.CopyInstructionFactory;
import org.gradle.api.tasks.util.ExistingDirsFilter;
import org.gradle.util.GUtil;

import java.util.*;
import java.io.File;

/**
 * Copies the content of the <code>sourceDirs</code> to the <code>destinationDir</code>.
 * You can define global or local includes, excludes or filters. Global ones apply to all
 * source dirs local ones only to a single source dir.
 *
 * @author Hans Dockter
 */
public class Copy extends ConventionTask {

    /**
     * A list of file objects denoting the directories to extract the content from.
     */
    private List srcDirs = null;

    /**
     * The directory where to copy then content from the source dirs.
     */
    private File destinationDir;

    /**
     * A set of include pattern (e.g. <code>'**//*.txt'</code>) which is applied to all source dirs.
     * The syntax of the include patterns is the same as the ant syntax for include patterns.
     */
    private Set<String> globalIncludes = new HashSet<String>();


    /**
     * A set of exclude pattern (e.g. <code>'**//*.txt'</code>) which is applied to all source dirs.
     * The syntax of the exclude patterns is the same as the ant syntax for exclude patterns.
     */
    private Set<String> globalExcludes = new HashSet<String>();

    /**
     * A map of filters which is applied to all source dirs. An example is: <code>[TODAY: new Date()]</code>,
     * which would replace the text content <code>@TODAY@</code> with the current date.
     * We use an Ant filterset to implement this. Right now you can't specify the token specifier, it's always @.
     */
    private Map<String, String> globalFilters = new HashMap<String, String>();

    /**
     * A set of include pattern (e.g. <code>'**//*.txt'</code>) which is applied to all source dirs.
     * The key of the map is the file object denoting the source dir. The value of the entry is a set of include
     * patterns.
     * The syntax of the include patterns is the same as the ant syntax for include patterns.
     */
    private Map<File, Set<String>> sourceDirIncludes = new HashMap<File, Set<String>>();
    /**
     * A map of exclude pattern (e.g. <code>'**//*.txt'</code>) which is applied to a single source dirs.
     * The key of the map is the file object denoting the source dir. The value of the entry is a set of exclude
     * patterns.
     * The syntax of the exclude patterns is the same as the ant syntax for exclude patterns.
     */
    private Map<File, Set<String>> sourceDirExcludes = new HashMap<File, Set<String>>();

    /**
     * A map of filters which is applied to a single source dirs. An example is: <code>[TODAY: new Date()]</code>,
     * which would replace the text content <code>@TODAY@</code> with the current date.
     * The key of the map is the file object denoting the source dir. The value of the entry is a map of filters.
     * We use an Ant filterset to implement this. Right now you can't specify the token specifier, it's always @.
     */
    private Map<File, Map<String, String>> sourceDirFilters = new HashMap<File, Map<String, String>>();

    // It is too cumbersone, if every user of this class has to set this property.
    // So we break IoC a little bit, but anyone can inject a new instance. And this is Groovy,
    // so we don't need an interface.
    CopyInstructionFactory copyInstructionFactory;

    ExistingDirsFilter existentDirsFilter = new ExistingDirsFilter();

    public Copy(Project project, String name) {
        super(project, name);
        copyInstructionFactory = new CopyInstructionFactory(project.getAnt());
        doLast(new TaskAction() {
            public void execute(Task task) {
                copyResources(task);
            }
        });
    }

    private void copyResources(Task task) {
        List<File> existingSourceDirs = existentDirsFilter.checkDestDirAndFindExistingDirsAndThrowStopActionIfNone(
                getDestinationDir(), getSrcDirs());

        for (File sourceDir : existingSourceDirs) {
            Set includes = GUtil.addSets(getSetFromMap(sourceDirIncludes, sourceDir), globalIncludes);
            Set excludes = GUtil.addSets(getSetFromMap(sourceDirExcludes, sourceDir), globalExcludes);
            Map filters = GUtil.addMaps(getMapFromMap(sourceDirFilters, sourceDir), globalFilters);
            copyInstructionFactory.createCopyInstruction(sourceDir, getDestinationDir(), includes, excludes, filters).execute();
        }
    }

    /**
     * adds the given sourceDirs to the sourceDirs property.
     */
    public Copy from(File... sourceDirs) {
        if (srcDirs == null) {
            srcDirs = new ArrayList();
        }
        srcDirs.addAll(Arrays.asList(sourceDirs));
        return this;
    }

    /**
     * sets the destination dir (equivalent to <code>resources.destinationDir = </code>
     */
    public Copy into(File destinationDir) {
        this.destinationDir = destinationDir;
        return this;
    }

    /**
     * Add global includes patterns applied to all source dirs.
     */
    public Copy includes(String... includes) {
        return includes(null, includes);
    }

    /**
     * Add includes patterns. If the sourceDir is specified the pattern is limited to the specified source dir.
     * Otherwise it is a global pattern applied to all source dirs.
     */
    public Copy includes(File sourceDir, String... includes) {
        return addIncludesExcludes(this.sourceDirIncludes, globalIncludes, sourceDir, includes);
    }

    /**
     * Add global excludes patterns applied to all source dirs.
     */
    public Copy excludes(String... excludes) {
        return excludes(null, excludes);
    }

    /**
     * Add excludes patterns. If the sourceDir is specified the pattern is limited to the specified source dir.
     * Otherwise it is a global pattern applied to all source dirs.
     */
    public Copy excludes(File sourceDir, String... excludes) {
        return addIncludesExcludes(this.sourceDirExcludes, globalExcludes, sourceDir, excludes);
    }

    /**
     * Add filters. If the sourceDir is specified the filters are limited to the specified source dir.
     * Otherwise they are global filters applied to all source dirs.
     */
    public Copy filter(Map filters) {
        return filter(null, filters);
    }

    /**
     * Add filters. If the sourceDir is specified the filters are limited to the specified source dir.
     * Otherwise they are global filters applied to all source dirs.
     */
    public Copy filter(File sourceDir, Map filters) {
        Map mapToAddTo = sourceDir != null ? getMapFromMap(sourceDirFilters, sourceDir) : globalFilters;
        mapToAddTo.putAll(filters);
        return this;
    }

    private Map getMapFromMap(Map map, Object key) {
        if (map.get(key) == null) {
            map.put(key, new HashMap());
        }
        return (Map) map.get(key);
    }

    private Copy addIncludesExcludes(Map map, Set globals, File sourceDir, String[] args) {
        Set listToAddTo = sourceDir != null ? getSetFromMap(map, sourceDir) : globals;
        listToAddTo.addAll(Arrays.asList(args));
        return this;
    }

    private Set getSetFromMap(Map map, Object key) {
        if (map.get(key) == null) {
            map.put(key, new HashSet());
        }
        return (Set) map.get(key);
    }

    public List getSrcDirs() {
        return (List) conv(srcDirs, "srcDirs");
    }

    public void setSrcDirs(List srcDirs) {
        this.srcDirs = srcDirs;
    }

    public File getDestinationDir() {
        return (File) conv(destinationDir, "destinationDir");
    }

    public void setDestinationDir(File destinationDir) {
        this.destinationDir = destinationDir;
    }

    public Set<String> getGlobalIncludes() {
        return globalIncludes;
    }

    public void setGlobalIncludes(Set<String> globalIncludes) {
        this.globalIncludes = globalIncludes;
    }

    public Set<String> getGlobalExcludes() {
        return globalExcludes;
    }

    public void setGlobalExcludes(Set<String> globalExcludes) {
        this.globalExcludes = globalExcludes;
    }

    public Map<String, String> getGlobalFilters() {
        return globalFilters;
    }

    public void setGlobalFilters(Map<String, String> globalFilters) {
        this.globalFilters = globalFilters;
    }

    public Map<File, Set<String>> getSourceDirIncludes() {
        return sourceDirIncludes;
    }

    public void setSourceDirIncludes(Map<File, Set<String>> sourceDirIncludes) {
        this.sourceDirIncludes = sourceDirIncludes;
    }

    public Map<File, Set<String>> getSourceDirExcludes() {
        return sourceDirExcludes;
    }

    public void setSourceDirExcludes(Map<File, Set<String>> sourceDirExcludes) {
        this.sourceDirExcludes = sourceDirExcludes;
    }

    public Map<File, Map<String, String>> getSourceDirFilters() {
        return sourceDirFilters;
    }

    public void setSourceDirFilters(Map<File, Map<String, String>> sourceDirFilters) {
        this.sourceDirFilters = sourceDirFilters;
    }
}
