/*
 * Copyright 2009 the original author or authors.
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

import groovy.lang.Closure;
import org.gradle.api.file.SourceSet;
import org.gradle.api.tasks.StopActionException;
import org.gradle.api.tasks.util.FileSet;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.util.ConfigureUtil;

import java.io.File;
import java.util.Set;

abstract class AbstractSourceSet implements SourceSet {

    public SourceSet matching(Closure filterConfigClosure) {
        PatternSet filter = new PatternSet();
        ConfigureUtil.configure(filterConfigClosure, filter);
        return new FilteredSourceSet(filter);
    }

    public SourceSet stopActionIfEmpty() {
        PatternSet patternSet = getPatternSet();
        for (File sourceDir : getExistingSourceDirs()) {
            FileSet fileset = new FileSet(sourceDir);
            fileset.setIncludes(patternSet.getIncludes());
            fileset.setExcludes(patternSet.getExcludes());
            if (!fileset.getFiles().isEmpty()) {
                return this;
            }
        }
        throw new StopActionException("No source files to operate on.");
    }

    protected PatternSet getPatternSet() {
        return new PatternSet();
    }

    protected abstract Set<File> getExistingSourceDirs();

    public Object addToAntBuilder(Object node, String childNodeName) {
        PatternSet patternSet = getPatternSet();
        for (File sourceDir : getExistingSourceDirs()) {
            FileSet fileset = new FileSet(sourceDir);
            fileset.setIncludes(patternSet.getIncludes());
            fileset.setExcludes(patternSet.getExcludes());
            fileset.addToAntBuilder(node, childNodeName);
        }
        return null;
    }

    private class FilteredSourceSet extends AbstractSourceSet {
        private final PatternSet patternSet;

        FilteredSourceSet(PatternSet patternSet) {
            this.patternSet = patternSet;
        }

        protected Set<File> getExistingSourceDirs() {
            return AbstractSourceSet.this.getExistingSourceDirs();
        }

        protected PatternSet getPatternSet() {
            return patternSet;
        }
    }
}

