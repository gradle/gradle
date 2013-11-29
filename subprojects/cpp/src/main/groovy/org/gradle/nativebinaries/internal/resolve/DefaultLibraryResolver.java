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

package org.gradle.nativebinaries.internal.resolve;

import org.gradle.api.InvalidUserDataException;
import org.gradle.nativebinaries.*;

class DefaultLibraryResolver implements LibraryResolver {
    private final NativeLibraryDependency dependency;

    private Flavor flavor;
    private ToolChain toolChain;
    private Platform platform;
    private BuildType buildType;

    public DefaultLibraryResolver(NativeLibraryDependency dependency) {
        this.dependency = dependency;
    }

    public LibraryResolver withFlavor(Flavor flavor) {
        this.flavor = flavor;
        return this;
    }

    // TODO:DAZ Remove this
    public LibraryResolver withToolChain(ToolChain toolChain) {
        this.toolChain = toolChain;
        return this;
    }

    public LibraryResolver withPlatform(Platform platform) {
        this.platform = platform;
        return this;
    }

    public LibraryResolver withBuildType(BuildType buildType) {
        this.buildType = buildType;
        return this;
    }


    public LibraryNativeDependencySet resolve() {
        Library library = dependency.getLibrary();
        Class<? extends LibraryBinary> type = dependency.getType();

        for (LibraryBinary candidate : library.getBinaries().withType(type)) {
            // TODO:DAZ This is a regression: if we have just one flavor then we don't care about matching flavors
            if (flavor != null && !flavor.getName().equals(candidate.getFlavor().getName())) {
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
