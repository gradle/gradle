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

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.CopyAction;
import org.gradle.api.tasks.util.PatternSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;

/**
 * @author Steve Appling
 */
public class CopyActionImpl extends CopySpecImpl implements CopyAction {
    private static Logger logger = LoggerFactory.getLogger(CopyActionImpl.class);

    private boolean caseSensitive = true;

    private CopyVisitor visitor = new CopyVisitor();

    public CopyActionImpl(FileResolver resolver) {
        super(resolver);
    }

    public void setVisitor(CopyVisitor visitor) {
        this.visitor = visitor;
    }

    public void execute() {
        copyAllSpecs();
    }

    public boolean getDidWork() {
        return visitor.getDidWork();
    }

    private void copyAllSpecs() {
        List<CopySpecImpl> specList = getLeafSyncSpecs();
        for (CopySpecImpl spec : specList) {
            copySingleSpec(spec);
        }
    }

    private void copySingleSpec(CopySpecImpl spec) {
        File destDir = spec.getDestDir();
        if (destDir == null) {
            logger.error("No destination dir for Copy task");
            throw new InvalidUserDataException("Error - no destination for Copy task, use 'into' to specify a target directory.");
        }

        visitor.visitSpec(spec);

        PatternSet patterns = new PatternSet();
        patterns.setCaseSensitive(caseSensitive);
        patterns.include(spec.getAllIncludes());
        patterns.exclude(spec.getAllExcludes());

        spec.getSource().matching(patterns).visit(visitor);
    }

    public void setCaseSensitive(boolean caseSensitive) {
        this.caseSensitive = caseSensitive;
    }
}
