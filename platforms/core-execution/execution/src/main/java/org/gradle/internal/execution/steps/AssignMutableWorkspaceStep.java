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

public class AssignMutableWorkspaceStep<C extends IdentityContext> extends MutableStep<C, WorkspaceResult> {
    private final Step<? super WorkspaceContext, ? extends CachingResult> delegate;

    public AssignMutableWorkspaceStep(Step<? super WorkspaceContext, ? extends CachingResult> delegate) {
        this.delegate = delegate;
    }

    @Override
    protected WorkspaceResult executeMutable(MutableUnitOfWork work, C context) {
        return work.getWorkspaceProvider().withWorkspace(
            context.getIdentity().getUniqueId(),
            workspace -> {
                WorkspaceContext delegateContext = new WorkspaceContext(context, workspace);
                CachingResult delegateResult = delegate.execute(work, delegateContext);
                return new WorkspaceResult(delegateResult, workspace);
            });
    }
}
