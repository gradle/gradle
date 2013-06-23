/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.plugins.ear.internal

import org.gradle.api.file.FileVisitDetails
import org.gradle.api.internal.file.copy.CopyAction
import org.gradle.api.internal.file.copy.CopySpecVisitor
import org.gradle.api.internal.file.copy.DelegatingCopySpecVisitor
import org.gradle.api.internal.file.copy.ReadableCopySpec

/**
 * Change the order in which CopySpecs are visited by deferring them until
 * one matches a given predicate. This exists for Ear.deploymentDescriptor
 * @author Kyle Mahan
 */
class ReorderingCopySpecVisitor extends DelegatingCopySpecVisitor {
    private boolean seenTargetSpec
    private List<Closure> deferredActions
    private Closure targetSpecPredicate

    ReorderingCopySpecVisitor(Closure targetSpecPredicate, CopySpecVisitor copySpecVisitor) {
        super(copySpecVisitor)
        this.targetSpecPredicate = targetSpecPredicate
    }

    public void startVisit(CopyAction action) {
        super.startVisit(action)
        seenTargetSpec = false
        deferredActions = []
    }

    public void endVisit() {
        processDeferredActions()
        super.endVisit()
    }

    public void visitSpec(ReadableCopySpec spec) {
        if (seenTargetSpec) {
            processDeferredActions()
        } else {
            seenTargetSpec = targetSpecPredicate(spec)
        }

        if (seenTargetSpec) {
            super.visitSpec(spec)
        } else {
            deferredActions << { super.visitSpec(spec) }
        }
    }

    public void visitDir(FileVisitDetails dirDetails) {
        if (seenTargetSpec) {
            super.visitDir(dirDetails)
        } else {
            deferredActions << { super.visitDir(dirDetails) }
        }
    }

    public void visitFile(FileVisitDetails visitDetails) {
        if (seenTargetSpec) {
            super.visitFile(visitDetails)
        } else {
            deferredActions << { super.visitFile(visitDetails) }
        }
    }

    private void processDeferredActions() {
        // unspool the deferred actions
        for (Closure deferred : deferredActions) {
            deferred()
        }
        deferredActions.clear()
    }

}
