/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.internal.file;

import org.gradle.api.file.DirectoryTree;
import org.gradle.api.tasks.util.PatternSet;

import java.io.File;

// Not quite immutable, see ImmutablePatternSet
public final class ImmutableDirectoryTree implements DirectoryTree {

    private final File dir;
    private final ImmutablePatternSet patternSet;

    public static ImmutableDirectoryTree of(DirectoryTree source) {
        if (source instanceof ImmutableDirectoryTree) {
            return (ImmutableDirectoryTree) source;
        } else {
            return of(source.getDir(), source.getPatterns());
        }
    }

    public static ImmutableDirectoryTree of(File dir, PatternSet patternSet) {
        return new ImmutableDirectoryTree(dir, patternSet);
    }

    private ImmutableDirectoryTree(File dir, PatternSet patternSet) {
        this.dir = dir;
        this.patternSet = ImmutablePatternSet.of(patternSet);
    }

    @Override
    public File getDir() {
        return dir;
    }

    @Override
    public ImmutablePatternSet getPatterns() {
        return patternSet;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ImmutableDirectoryTree that = (ImmutableDirectoryTree) o;

        if (dir != null ? !dir.equals(that.dir) : that.dir != null) {
            return false;
        }
        return !(patternSet != null ? !patternSet.equals(that.patternSet) : that.patternSet != null);
    }

    @Override
    public int hashCode() {
        int result = dir != null ? dir.hashCode() : 0;
        result = 31 * result + (patternSet != null ? patternSet.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "dir: " + dir.getPath();
    }
}
