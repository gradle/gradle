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

package org.gradle.nativebinaries.internal;

import org.gradle.api.InvalidUserDataException;
import org.gradle.nativebinaries.*;

class DefaultLibraryResolver implements ContextualLibraryResolver {
    private final Library library;

    private Flavor flavor = new DefaultFlavor(DefaultFlavor.DEFAULT);
    private ToolChain toolChain;
    private Platform platform;
    private BuildType buildType;
    private Class<? extends LibraryBinary> type = SharedLibraryBinary.class;

    public DefaultLibraryResolver(Library library) {
        this.library = library;
    }

    public ContextualLibraryResolver withFlavor(Flavor flavor) {
        this.flavor = flavor;
        return this;
    }

    public ContextualLibraryResolver withToolChain(ToolChain toolChain) {
        this.toolChain = toolChain;
        return this;
    }

    public ContextualLibraryResolver withPlatform(Platform platform) {
        this.platform = platform;
        return this;
    }

    public ContextualLibraryResolver withBuildType(BuildType buildType) {
        this.buildType = buildType;
        return this;
    }

    public ContextualLibraryResolver withType(Class<? extends LibraryBinary> type) {
        this.type = type;
        return this;
    }

    public NativeDependencySet resolve() {
        for (LibraryBinary candidate : library.getBinaries().withType(type)) {
            // If the library has > 1 flavor, then flavor must match
            if (library.getFlavors().size() > 1 && !flavor.getName().equals(candidate.getFlavor().getName())) {
                continue;
            }
            // TODO:DAZ Matching should be more sophisticated for toolChain, platform and buildType
            if (toolChain != null && !toolChain.getName().equals(candidate.getToolChain().getName())) {
                continue;
            }
            if (platform != null && !platform.getName().equals(candidate.getTargetPlatform().getName())) {
                continue;
            }
            if (buildType != null && !buildType.getName().equals(candidate.getBuildType().getName())) {
                continue;
            }

            return candidate.resolve();
        }

        String typeName = type == SharedLibraryBinary.class ? "shared" : "static";
        throw new InvalidUserDataException(String.format("No %s library binary available for %s with [flavor: '%s', toolChain: '%s', platform: '%s']",
                typeName, library, flavor.getName(), toolChain.getName(), platform.getName()));
    }
}
