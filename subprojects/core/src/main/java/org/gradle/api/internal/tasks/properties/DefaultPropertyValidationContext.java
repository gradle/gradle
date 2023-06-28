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

package org.gradle.api.internal.tasks.properties;

import org.gradle.api.Action;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.internal.file.ReservedFileSystemLocationRegistry;
import org.gradle.internal.reflect.validation.TypeAwareProblemBuilder;
import org.gradle.internal.reflect.validation.TypeValidationContext;

import java.io.File;

public class DefaultPropertyValidationContext implements PropertyValidationContext, TypeValidationContext {
    private final PathToFileResolver fileResolver;
    private final ReservedFileSystemLocationRegistry reservedFileSystemLocationRegistry;
    private final TypeValidationContext delegate;

    public DefaultPropertyValidationContext(PathToFileResolver fileResolver, ReservedFileSystemLocationRegistry reservedFileSystemLocationRegistry, TypeValidationContext delegate) {
        this.fileResolver = fileResolver;
        this.reservedFileSystemLocationRegistry = reservedFileSystemLocationRegistry;
        this.delegate = delegate;
    }

    @Override
    public void visitTypeProblem(Action<? super TypeAwareProblemBuilder> problemSpec) {
        delegate.visitTypeProblem(problemSpec);
    }

    @Override
    public void visitPropertyProblem(Action<? super TypeAwareProblemBuilder> problemSpec) {
        delegate.visitPropertyProblem(problemSpec);
    }

    @Override
    public PathToFileResolver getFileResolver() {
        return fileResolver;
    }

    @Override
    public boolean isInReservedFileSystemLocation(File location) {
        return reservedFileSystemLocationRegistry.isInReservedFileSystemLocation(location);
    }
}
