/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.instrumentation.reporting.listener;

import org.gradle.internal.instrumentation.api.types.BytecodeInterceptorType;

import javax.annotation.Nullable;
import java.io.File;

public class BytecodeUpgradeReportMethodInterceptionListener implements MethodInterceptionListener, AutoCloseable {

    private final MethodInterceptionListener delegate;

    public BytecodeUpgradeReportMethodInterceptionListener(File output) {
        this(null, output);
    }

    public BytecodeUpgradeReportMethodInterceptionListener(@Nullable File source, File output) {
        this.delegate = new FileOutputMethodInterceptionListener(source, output);
    }

    @Override
    public void onInterceptedMethodInstruction(BytecodeInterceptorType type, String sourceFileName, String relativePath, String owner, String name, String descriptor, int lineNumber) {
        if (type == BytecodeInterceptorType.BYTECODE_UPGRADE_REPORT) {
            delegate.onInterceptedMethodInstruction(type, sourceFileName, relativePath, owner, name, descriptor, lineNumber);
        }
    }

    @Override
    public void close() {
        if (delegate instanceof AutoCloseable) {
            try {
                ((AutoCloseable) delegate).close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
