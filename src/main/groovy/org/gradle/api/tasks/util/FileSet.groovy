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

package org.gradle.api.tasks.util

import org.gradle.api.InvalidUserDataException
import org.gradle.api.internal.file.AbstractFileCollection
import org.gradle.api.tasks.AntBuilderAware
import org.gradle.api.internal.tasks.copy.BreadthFirstDirectoryWalker
import org.gradle.api.internal.tasks.copy.FileVisitor

/**
 * @author Hans Dockter
 */
// todo rename dir to base
class FileSet extends AbstractFileCollection implements PatternFilterable, AntBuilderAware {
    final PatternSet patternSet = new PatternSet()
    File dir

    FileSet() {
        this((File) null)
    }

    FileSet(File dir) {
        this.dir = dir
    }

    FileSet(Map args) {
        transformToFile(args).each {String key, value ->
            this."$key" = value
        }
    }

    public String getDisplayName() {
        "file set '$dir'"
    }

    public Set<File> getFiles() {
        Set<File> files = new LinkedHashSet<File>()
        FileVisitor visitor = [visitFile: {file, path -> files.add(file)}, visitDir: {file, path -> }] as FileVisitor
        BreadthFirstDirectoryWalker walker = new BreadthFirstDirectoryWalker(true, visitor)
        walker.addIncludes(patternSet.includes)
        walker.addExcludes(patternSet.excludes)
        walker.start(dir)
        files
    }

    public Set<String> getIncludes() {
        patternSet.includes
    }

    public PatternFilterable setIncludes(Iterable<String> includes) {
        patternSet.setIncludes(includes)
        this
    }

    public Set<String> getExcludes() {
        patternSet.excludes
    }

    public PatternFilterable setExcludes(Iterable<String> excludes) {
        patternSet.setExcludes(excludes)
        this
    }

    public PatternFilterable include(String ... includes) {
        patternSet.include(includes)
        this
    }

    public PatternFilterable include(Iterable<String> includes) {
        patternSet.include(includes)
        this
    }

    public PatternFilterable exclude(String ... excludes) {
        patternSet.exclude(excludes)
        this
    }

    public PatternFilterable exclude(Iterable<String> excludes) {
        patternSet.exclude(excludes)
        this
    }

    private static Map transformToFile(Map args) {
        if (!args.dir) { throw new InvalidUserDataException ('A basedir must be specified in the task or via a method argument!') }
        Map newArgs = new HashMap(args)
        newArgs.dir = new File(newArgs.dir.toString())
        newArgs
    }

    def addToAntBuilder(node, String childNodeName = null) {
        node."${childNodeName ?: 'fileset'}"(dir: dir.absolutePath) {
            patternSet.addIncludesAndExcludesToBuilder(delegate)
        }
    }

}
