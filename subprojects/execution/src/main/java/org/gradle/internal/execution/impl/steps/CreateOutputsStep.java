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

package org.gradle.internal.execution.impl.steps;

import org.gradle.api.file.FileCollection;
import org.gradle.internal.execution.ExecutionResult;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.UnitOfWork.OutputType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;

import static org.gradle.util.GFileUtils.mkdirs;

public class CreateOutputsStep<C extends Context> implements Step<C> {
    private static final Logger LOGGER = LoggerFactory.getLogger(CreateOutputsStep.class);

    private final Step<? super C> delegate;

    public CreateOutputsStep(Step<? super C> delegate) {
        this.delegate = delegate;
    }

    @Override
    public ExecutionResult execute(C context) {
        context.getWork().visitOutputs(new UnitOfWork.OutputVisitor() {
            @Override
            public void visitOutput(String name, OutputType type, FileCollection files, @Nullable File root) {
                for (File outputRoot : files) {
                    ensureOutput(name, outputRoot, type);
                }
            }
        });
        return delegate.execute(context);
    }

    private static void ensureOutput(String name, @Nullable File outputRoot, OutputType type) {
        if (outputRoot == null) {
            LOGGER.debug("Not ensuring directory exists for property {}, because value is null", name);
            return;
        }
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
