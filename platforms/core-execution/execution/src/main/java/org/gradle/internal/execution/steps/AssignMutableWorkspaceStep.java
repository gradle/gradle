/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.execution.steps;

import org.gradle.internal.execution.MutableUnitOfWork;
import org.gradle.internal.execution.UnitOfWork;

public class AssignMutableWorkspaceStep<C extends IdentityContext> implements Step<C, WorkspaceResult> {
    private final Step<? super WorkspaceContext, ? extends CachingResult> delegate;

    public AssignMutableWorkspaceStep(Step<? super WorkspaceContext, ? extends CachingResult> delegate) {
        this.delegate = delegate;
    }

    @Override
    public WorkspaceResult execute(UnitOfWork work, C context) {
        return ((MutableUnitOfWork) work).getWorkspaceProvider().withWorkspace(
            context.getIdentity().getUniqueId(),
            (workspace, history) -> {
                CachingResult delegateResult = delegate.execute(work, new WorkspaceContext(context, workspace, history));
                return new WorkspaceResult(delegateResult, workspace);
            });
    }
}
