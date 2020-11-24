/*
 * Copyright 2018 the original author or authors.
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

import com.google.common.collect.ImmutableList;
import org.gradle.api.file.FileCollection;
import org.gradle.internal.execution.OutputChangeListener;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.file.TreeType;

import java.io.File;

public class BroadcastChangingOutputsStep<C extends WorkspaceContext, R extends Result> implements Step<C, R> {

    private final OutputChangeListener outputChangeListener;
    private final Step<? super C, ? extends R> delegate;

    public BroadcastChangingOutputsStep(
        OutputChangeListener outputChangeListener,
        Step<? super C, ? extends R> delegate
    ) {
        this.outputChangeListener = outputChangeListener;
        this.delegate = delegate;
    }

    @Override
    public R execute(UnitOfWork work, C context) {
        ImmutableList.Builder<String> builder = ImmutableList.builder();
        work.visitOutputs(context.getWorkspace(), new UnitOfWork.OutputVisitor() {
            @Override
            public void visitOutputProperty(String propertyName, TreeType type, File root, FileCollection contents) {
                builder.add(root.getAbsolutePath());
            }

            @Override
            public void visitLocalState(File localStateRoot) {
                builder.add(localStateRoot.getAbsolutePath());
            }

            @Override
            public void visitDestroyable(File destroyableRoot) {
                builder.add(destroyableRoot.getAbsolutePath());
            }
        });
        outputChangeListener.beforeOutputChange(builder.build());
        return delegate.execute(work, context);
    }
}
