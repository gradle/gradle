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
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.RelativePath;
import org.gradle.util.DeprecationLogger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Maintains a set of relative paths that has been seen and optionally
 * excludes duplicates (based on that path).
 * @author Kyle Mahan
 */
public class DuplicateHandlingCopySpecVisitor extends DelegatingCopySpecVisitor {

    private final Set<RelativePath> visitedFiles = new HashSet<RelativePath>();
    private Map<RelativePath, PossibleDuplicate> deferred = new HashMap<RelativePath, PossibleDuplicate>();
    private ReadableCopySpec spec;
    private boolean warnOnIncludeDuplicate;

    public DuplicateHandlingCopySpecVisitor(CopySpecVisitor visitor, boolean warnOnIncludeDuplicate) {
        super(visitor);
        this.warnOnIncludeDuplicate = warnOnIncludeDuplicate;
    }

    public void startVisit(CopyAction action) {
        deferred.clear();
        super.startVisit(action);
    }


    public void endVisit() {
        visitDeferred();
        deferred.clear();
        super.endVisit();
    }

    public void visitSpec(ReadableCopySpec spec) {
        this.spec = spec;
        super.visitSpec(spec);
    }

    public void visitFile(FileVisitDetails visitDetails) {
        FileCopyDetails details = (FileCopyDetails) visitDetails;
        DuplicatesStrategy strategy = determineStrategy(details);
        if (strategy == DuplicatesStrategy.exclude) {
            maybeDefer(visitDetails);
            return;
        }
        if (warnOnIncludeDuplicate && !visitedFiles.add(details.getRelativePath())) {
            DeprecationLogger.nagUserOfDeprecatedBehaviour(
                    String.format("Including duplicate file %s", details.getRelativePath()));
        }
        super.visitFile(visitDetails);
    }

    private void maybeDefer(FileVisitDetails details) {
        PossibleDuplicate oldDuplicate = deferred.get(details.getRelativePath());
        PossibleDuplicate newDuplicate = new PossibleDuplicate(details, spec, spec.getDuplicatesPriority());
        if (oldDuplicate == null || newDuplicate.isHigherPriority(oldDuplicate)) {
            deferred.put(details.getRelativePath(), newDuplicate);
        }
    }

    private void visitDeferred() {
        for (PossibleDuplicate kept : deferred.values()) {
            super.visitSpec(kept.copySpec);
            super.visitFile(kept.details);
        }
    }


    private DuplicatesStrategy determineStrategy(FileCopyDetails details) {
        if (details.getDuplicatesStrategy() == null
                || details.getDuplicatesStrategy() == DuplicatesStrategy.inherit) {
            return spec.getDuplicatesStrategy();
        }
        return details.getDuplicatesStrategy();
    }

    private static final class PossibleDuplicate {
        public FileVisitDetails details;
        public ReadableCopySpec copySpec;
        public Integer priority;

        public PossibleDuplicate(FileVisitDetails details, ReadableCopySpec spec, Integer priority) {
            this.details = details;
            this.copySpec = spec;
            this.priority = priority;
        }

        public boolean isHigherPriority(PossibleDuplicate o) {
            return priority != null && (o.priority == null || priority > o.priority);
        }
    }

}
