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
package org.gradle.api.internal.file.copy;

import org.gradle.api.file.FileCopyDetails;

public class DelegatingCopySpecContentVisitor implements CopySpecContentVisitor {
    private final CopySpecContentVisitor visitor;

    public DelegatingCopySpecContentVisitor(CopySpecContentVisitor visitor) {
        this.visitor = visitor;
    }

    protected CopySpecContentVisitor getVisitor() {
        return visitor;
    }

    public void startVisit() {
        getVisitor().startVisit();
    }

    public void endVisit() {
        getVisitor().endVisit();
    }

    public void visitSpec(CopySpecInternal spec) {
        getVisitor().visitSpec(spec);
    }

    public void visitDir(FileCopyDetails dirDetails) {
        getVisitor().visitDir(dirDetails);
    }

    public void visitFile(FileCopyDetails fileDetails) {
        getVisitor().visitFile(fileDetails);
    }

    public boolean getDidWork() {
        return getVisitor().getDidWork();
    }
}
