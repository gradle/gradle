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
import org.gradle.internal.reflect.WorkValidationContext;

import javax.annotation.Nullable;
import java.io.File;

public class DefaultTaskValidationContext implements TaskValidationContext, WorkValidationContext {
    private final FileOperations fileOperations;
    private final ReservedFileSystemLocationRegistry reservedFileSystemLocationRegistry;
    private final WorkValidationContext delegate;

    public DefaultTaskValidationContext(FileOperations fileOperations, ReservedFileSystemLocationRegistry reservedFileSystemLocationRegistry, WorkValidationContext delegate) {
        this.fileOperations = fileOperations;
        this.reservedFileSystemLocationRegistry = reservedFileSystemLocationRegistry;
        this.delegate = delegate;
    }

    @Override
    public void visitWarning(@Nullable String ownerPath, String propertyName, String message) {
        delegate.visitWarning(ownerPath, propertyName, message);
    }

    @Override
    public void visitWarning(String message) {
        delegate.visitWarning(message);
    }

    @Override
    public void visitError(@Nullable String ownerPath, String propertyName, String message) {
        delegate.visitError(ownerPath, propertyName, message);
    }

    @Override
    public void visitError(String message) {
        delegate.visitError(message);
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
