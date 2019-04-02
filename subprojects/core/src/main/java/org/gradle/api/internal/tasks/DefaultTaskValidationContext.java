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

import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.tasks.properties.DefaultParameterValidationContext;
import org.gradle.internal.file.ReservedFileSystemLocationRegistry;

import java.io.File;
import java.util.Collection;

public class DefaultTaskValidationContext extends DefaultParameterValidationContext implements TaskValidationContext {
    private final FileResolver resolver;
    private final ReservedFileSystemLocationRegistry reservedFileSystemLocationRegistry;

    public DefaultTaskValidationContext(FileResolver resolver, ReservedFileSystemLocationRegistry reservedFileSystemLocationRegistry, Collection<String> messages) {
        super(messages);
        this.resolver = resolver;
        this.reservedFileSystemLocationRegistry = reservedFileSystemLocationRegistry;
    }

    @Override
    public FileResolver getResolver() {
        return resolver;
    }

    @Override
    public boolean isInReservedFileSystemLocation(File location) {
        return reservedFileSystemLocationRegistry.isInReservedFileSystemLocation(location);
    }
}
