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

import org.gradle.api.file.CopyAction;
import org.gradle.api.file.FileTree;
import org.gradle.api.tasks.util.PatternSet;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Steve Appling
 */
public class CopyActionImpl extends CopySpecImpl implements CopyAction {
    private boolean caseSensitive = true;
    private CopySpecVisitor visitor;

    public CopyActionImpl(FileResolver resolver, CopySpecVisitor visitor) {
        super(resolver);
        this.visitor = new MappingCopySpecVisitor(visitor);
    }

    public void setVisitor(CopySpecVisitor visitor) {
        this.visitor = visitor;
    }

    public void execute() {
        visitor.startVisit(this);
        for (CopySpecImpl spec : getAllSpecs()) {
            copySingleSpec(spec);
        }
        visitor.endVisit();
    }

    public boolean getDidWork() {
        return visitor.getDidWork();
    }

    public FileTree getAllSource() {
        List<FileTree> sources = new ArrayList<FileTree>();
        for (CopySpecImpl spec : getAllSpecs()) {
            FileTree source = getSource(spec);
            sources.add(source);
        }
        return getResolver().resolveFilesAsTree(sources);
    }

    private void copySingleSpec(CopySpecImpl spec) {
        visitor.visitSpec(spec);

        FileTree source = getSource(spec);
        source.visit(visitor);
    }

    private FileTree getSource(CopySpecImpl spec) {
        PatternSet patterns = new PatternSet();
        patterns.setCaseSensitive(caseSensitive);
        patterns.include(spec.getAllIncludes());
        patterns.includeSpecs(spec.getAllIncludeSpecs());
        patterns.exclude(spec.getAllExcludes());
        patterns.excludeSpecs(spec.getAllExcludeSpecs());
        return spec.getSource().matching(patterns);
    }

    public void setCaseSensitive(boolean caseSensitive) {
        this.caseSensitive = caseSensitive;
    }
}
