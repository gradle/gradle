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

import org.gradle.api.file.FileCollection;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.file.TreeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

import static org.gradle.util.internal.GFileUtils.mkdirs;

public class CreateOutputsStep<C extends WorkspaceContext, R extends Result> implements Step<C, R> {
    private static final Logger LOGGER = LoggerFactory.getLogger(CreateOutputsStep.class);

    private final Step<? super C, ? extends R> delegate;

    public CreateOutputsStep(Step<? super C, ? extends R> delegate) {
        this.delegate = delegate;
    }

    @Override
    public R execute(UnitOfWork work, C context) {
        work.visitOutputs(context.getWorkspace(), new UnitOfWork.OutputVisitor() {
            @Override
            public void visitOutputProperty(String propertyName, TreeType type, File root, FileCollection contents) {
                ensureOutput(propertyName, root, type);
            }

            @Override
            public void visitLocalState(File localStateRoot) {
                ensureOutput("local state", localStateRoot, TreeType.FILE);
            }
        });
        return delegate.execute(work, context);
    }

    private static void ensureOutput(String name, File outputRoot, TreeType type) {
        switch (type) {
            case DIRECTORY:
                LOGGER.debug("Ensuring directory exists for property {} at {}", name, outputRoot);
                mkdirs(outputRoot);
                break;
            case FILE:
                LOGGER.debug("Ensuring parent directory exists for property {} at {}", name, outputRoot);
                mkdirs(outputRoot.getParentFile());
                break;
            default:
                throw new AssertionError();
        }
    }
}
