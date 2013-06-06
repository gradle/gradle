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

package org.gradle.nativecode.base.internal;

import org.gradle.internal.os.OperatingSystem;

public abstract class AbstractToolChain implements ToolChainInternal {
    private final OperatingSystem operatingSystem;
    private ToolChainAvailability availability;

    protected AbstractToolChain(OperatingSystem operatingSystem) {
        this.operatingSystem = operatingSystem;
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
            throw new IllegalStateException(String.format("Tool chain %s is not available", getName()));
        }
    }

    public OperatingSystem getOperatingSystem() {
        return operatingSystem;
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
}
