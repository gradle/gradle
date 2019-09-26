/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.tasks;

import org.gradle.api.internal.file.FileOperations;
import org.gradle.internal.file.ReservedFileSystemLocationRegistry;
import org.gradle.internal.reflect.TypeValidationContext;

import javax.annotation.Nullable;
import java.io.File;

public class DefaultTaskValidationContext implements TaskValidationContext, TypeValidationContext {
    private final FileOperations fileOperations;
    private final ReservedFileSystemLocationRegistry reservedFileSystemLocationRegistry;
    private final TypeValidationContext delegate;

    public DefaultTaskValidationContext(FileOperations fileOperations, ReservedFileSystemLocationRegistry reservedFileSystemLocationRegistry, TypeValidationContext delegate) {
        this.fileOperations = fileOperations;
        this.reservedFileSystemLocationRegistry = reservedFileSystemLocationRegistry;
        this.delegate = delegate;
    }

    @Override
    public void visitTypeProblem(Severity severity, Class<?> type, String message) {
        delegate.visitTypeProblem(severity, type, message);
    }

    @Override
    public void visitPropertyProblem(Severity severity, @Nullable String parentProperty, @Nullable String property, String message) {
        delegate.visitPropertyProblem(severity, parentProperty, property, message);
    }

    @Override
    public FileOperations getFileOperations() {
        return fileOperations;
    }

    @Override
    public boolean isInReservedFileSystemLocation(File location) {
        return reservedFileSystemLocationRegistry.isInReservedFileSystemLocation(location);
    }
}
