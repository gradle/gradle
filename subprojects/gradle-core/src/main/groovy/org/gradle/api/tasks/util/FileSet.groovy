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
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.file.FileTree
import org.gradle.api.file.FileVisitor
import org.gradle.api.tasks.WorkResult
import org.gradle.util.ConfigureUtil
import org.gradle.api.internal.file.*

/**
 * @author Hans Dockter
 */
class FileSet extends AbstractFileTree implements ConfigurableFileTree {
    PatternSet patternSet = new PatternSet()
    private File dir
    FileResolver resolver

    FileSet(Object dir, FileResolver resolver) {
        this([dir: dir], resolver)
    }

    FileSet(Map args, FileResolver resolver) {
        this.resolver = resolver ?: new IdentityFileResolver()
        args.each {String key, value ->
            this.setProperty(key, value)
        }
    }

    public FileSet setDir(Object dir) {
        from(dir)
    }

    public File getDir() {
        if (!dir) { throw new InvalidUserDataException('A base directory must be specified in the task or via a method argument!') }
        dir
    }

    public FileSet from(Object dir) {
        this.dir = resolver.resolve(dir)
        this
    }

    public String getDisplayName() {
        "file set '$dir'"
    }

    FileTree matching(PatternFilterable patterns) {
        PatternSet patternSet = this.patternSet.intersect()
        patternSet.copyFrom(patterns)
        FileSet filtered = new FileSet(getDir(), resolver)
        filtered.patternSet = patternSet
        filtered
    }

    FileTree visit(FileVisitor visitor) {
        BreadthFirstDirectoryWalker walker = new BreadthFirstDirectoryWalker(visitor)
        walker.match(patternSet).start(getDir())
        this
    }

    public WorkResult copy(Closure closure) {
        CopyActionImpl action = new CopyActionImpl(resolver)
        action.from(getDir())
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

    protected void addAsFileSet(Object builder, String nodeName) {
        File dir = getDir()
        if (!dir.exists()) {
            return
        }
        doAddFileSet(builder, dir, nodeName);
    }

    protected void addAsResourceCollection(Object builder, String nodeName) {
        addAsFileSet(builder, nodeName)
    }

    protected Collection<FileSet> getAsFileSets() {
        return getDir().exists() ? [this] : []
    }

    protected def doAddFileSet(Object builder, File dir, String nodeName) {
        builder."${nodeName ?: 'fileset'}"(dir: dir.absolutePath) {
            patternSet.addToAntBuilder(builder)
        }
    }
}
