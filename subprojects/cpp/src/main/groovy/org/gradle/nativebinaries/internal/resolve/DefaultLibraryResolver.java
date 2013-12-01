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
import org.gradle.util.GUtil;

import java.util.Set;

class DefaultLibraryResolver implements LibraryResolver {
    private final NativeLibraryRequirement requirement;
    private final NativeBinary context;
    private final LibraryBinaryLocator libraryBinaryLocator;

    public DefaultLibraryResolver(LibraryBinaryLocator libraryBinaryLocator, NativeLibraryRequirement requirement, NativeBinary context) {
        this.requirement = requirement;
        this.context = context;
        this.libraryBinaryLocator = libraryBinaryLocator;
    }

    public LibraryNativeDependencySet resolve() {
        return new LibraryResolution()
                .withFlavor(context.getFlavor())
                .withPlatform(context.getTargetPlatform())
                .withBuildType(context.getBuildType())
                .resolve(libraryBinaryLocator.getCandidateBinaries(requirement));
    }

    private class LibraryResolution {
        private Flavor flavor;
        private Platform platform;
        private BuildType buildType;

        public LibraryResolution withFlavor(Flavor flavor) {
            this.flavor = flavor;
            return this;
        }

        public LibraryResolution withPlatform(Platform platform) {
            this.platform = platform;
            return this;
        }

        public LibraryResolution withBuildType(BuildType buildType) {
            this.buildType = buildType;
            return this;
        }

        public LibraryNativeDependencySet resolve(Set<? extends LibraryBinary> candidates) {
            for (LibraryBinary candidate : candidates) {
                // TODO:DAZ This is a regression: if we have just one flavor then we don't care about matching flavors
                if (flavor != null && !flavor.getName().equals(candidate.getFlavor().getName())) {
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

            String typeName = GUtil.elvis(requirement.getLinkage(), "shared");
            throw new InvalidUserDataException(String.format("No %s library binary available for library '%s' with [flavor: '%s', platform: '%s']",
                    typeName, requirement.getLibraryName(), flavor.getName(), platform.getName()));
        }
    }
}
