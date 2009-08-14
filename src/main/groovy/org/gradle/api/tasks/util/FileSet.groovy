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
import org.gradle.api.file.FileTree
import org.gradle.api.internal.file.AbstractFileCollection
import org.gradle.api.internal.file.BreadthFirstDirectoryWalker
import org.gradle.api.internal.file.FileVisitor
import org.gradle.api.internal.file.UnionFileTree
import org.gradle.util.ConfigureUtil
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.file.IdentityFileResolver
import org.gradle.api.internal.file.CopyActionImpl
import org.gradle.api.tasks.WorkResult

/**
 * @author Hans Dockter
 */
// todo rename dir to base
class FileSet extends AbstractFileCollection implements FileTree, PatternFilterable {
    PatternSet patternSet = new PatternSet()
    File dir

    /* Note, FileResolver is needed to support the copy operation.  This is set if the FileSet
     is constructed using one of the Project.fileSet methods.  There are currently several uses
     of FileSet where they are constructed without an appropriate resolver.  In these cases, the
     IdentityFileResolver is used, but these instances will probably not have the best copy semantics.
     FileSet.copy will evaluate the sources and destinations relative to the current working
     directory, not the project.

     TODO - we need to find a way to consistently get the projects FileResolver into all FileCollections
     */
    FileResolver resolver

    FileSet() {
        this((FileResolver)null)
    }

    FileSet(FileResolver resolver) {
        if (resolver != null) {
            this.resolver = resolver
        } else {
            this.resolver = new IdentityFileResolver()
        }
    }

    FileSet(File dir) {
        this((FileResolver)null)
        from(dir)
    }

    FileSet(Map args, FileResolver resolver=null) {
        this(resolver)
        transformToFile(args).each {String key, value ->
            this."$key" = value
        }
    }

    public void setDir(Object baseDir) {
        from(baseDir)
    }

    public void from(Object baseDir) {
        dir = resolver.resolve(baseDir);
    }

    public String getDisplayName() {
        "file set '$dir'"
    }

    public Set<File> getFiles() {
        Set<File> files = new LinkedHashSet<File>()
        FileVisitor visitor = [visitFile: {file, path -> files.add(file)}, visitDir: {file, path -> }] as FileVisitor
        BreadthFirstDirectoryWalker walker = new BreadthFirstDirectoryWalker(visitor)
        walker.match(patternSet).start(dir)
        files
    }

    FileTree plus(FileTree fileTree) {
        return new UnionFileTree(this, fileTree)
    }

    FileTree matching(Closure filterConfigClosure) {
        PatternSet patternSet = this.patternSet.intersect()
        ConfigureUtil.configure(filterConfigClosure, patternSet)
        FileSet filtered = new FileSet(dir)
        filtered.patternSet = patternSet
        filtered
    }

    public WorkResult copy(Closure closure) {
        CopyActionImpl action = new CopyActionImpl(resolver)
        action.from(dir)
        action.include(getIncludes())
        action.exclude(getExcludes())
        ConfigureUtil.configure(closure, action)
        action.execute()
        return action
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
            patternSet.addToAntBuilder(node)
        }
    }
}
