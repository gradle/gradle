/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.file.copy;

import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.file.FileCopyDetails;
import org.gradle.api.file.RelativePath;
import org.gradle.util.DeprecationLogger;

import java.util.HashSet;
import java.util.Set;

/**
 * Maintains a set of relative paths that has been seen and optionally
 * excludes duplicates (based on that path).
 * @author Kyle Mahan
 */
public class DuplicateHandlingCopySpecVisitor extends DelegatingCopySpecVisitor {

    private final Set<RelativePath> visitedFiles = new HashSet<RelativePath>();
    private CopySpecInternal spec;
    private boolean warnOnIncludeDuplicate;

    public DuplicateHandlingCopySpecVisitor(CopySpecVisitor visitor, boolean warnOnIncludeDuplicate) {
        super(visitor);
        this.warnOnIncludeDuplicate = warnOnIncludeDuplicate;
    }

    public void visitSpec(CopySpecInternal spec) {
        this.spec = spec;
        super.visitSpec(spec);
    }

    public void visitFile(FileCopyDetails details) {
        DuplicatesStrategy strategy = determineStrategy(details);

        if (!visitedFiles.add(details.getRelativePath())) {
            if (strategy == DuplicatesStrategy.EXCLUDE) {
                return;
            }
            if (warnOnIncludeDuplicate) {
                DeprecationLogger.nagUserOfDeprecatedBehaviour(
                        String.format("Including duplicate file %s", details.getRelativePath()));
            }
        }

        super.visitFile(details);
    }


    private DuplicatesStrategy determineStrategy(FileCopyDetails details) {
        if (details.getDuplicatesStrategy() == null) {
            return spec.getDuplicatesStrategy();
        }
        return details.getDuplicatesStrategy();
    }

}
