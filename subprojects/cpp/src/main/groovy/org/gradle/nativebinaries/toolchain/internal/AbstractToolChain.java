/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.nativebinaries.toolchain.internal;

import org.gradle.api.internal.file.FileResolver;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.nativebinaries.internal.ToolChainAvailability;
import org.gradle.nativebinaries.internal.ToolChainInternal;

import java.io.File;

public abstract class AbstractToolChain implements ToolChainInternal {
    private final String name;
    protected final OperatingSystem operatingSystem;
    private final FileResolver fileResolver;
    private ToolChainAvailability availability;

    protected AbstractToolChain(String name, OperatingSystem operatingSystem, FileResolver fileResolver) {
        this.name = name;
        this.operatingSystem = operatingSystem;
        this.fileResolver = fileResolver;
    }

    public String getName() {
        return name;
    }

    protected abstract String getTypeName();

    @Override
    public String toString() {
        return String.format("ToolChain '%s' (%s)", getName(), getTypeName());
    }

    public ToolChainAvailability getAvailability() {
        if (availability == null) {
            availability = new ToolChainAvailability();
            checkAvailable(availability);
        }
        return availability;
    }

    protected void checkAvailable() {
        if (!getAvailability().isAvailable()) {
            throw new IllegalStateException(String.format("Tool chain %s is not available: %s", getName(), getAvailability().getUnavailableMessage()));
        }
    }

    public String getOutputType() {
        return String.format("%s-%s", getName(), operatingSystem.getName());
    }

    protected abstract void checkAvailable(ToolChainAvailability availability);

    public String getExecutableName(String executablePath) {
        return operatingSystem.getExecutableName(executablePath);
    }

    public String getSharedLibraryName(String libraryName) {
        return operatingSystem.getSharedLibraryName(libraryName);
    }

    public String getSharedLibraryLinkFileName(String libraryName) {
        return getSharedLibraryName(libraryName);
    }

    public String getStaticLibraryName(String libraryName) {
        return operatingSystem.getStaticLibraryName(libraryName);
    }

    protected File resolve(Object path) {
        return fileResolver.resolve(path);
    }
}
