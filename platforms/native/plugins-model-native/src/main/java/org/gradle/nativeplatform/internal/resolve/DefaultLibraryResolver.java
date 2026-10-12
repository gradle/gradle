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
package org.gradle.nativeplatform.internal.resolve;

import org.gradle.api.DomainObjectSet;
import org.gradle.api.InvalidUserDataException;
import org.gradle.language.base.internal.resolve.LibraryResolveException;
import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.util.internal.GUtil;

import java.util.Set;

@SuppressWarnings("deprecation")
class DefaultLibraryResolver {
    private final org.gradle.nativeplatform.NativeLibraryRequirement requirement;
    private final org.gradle.nativeplatform.NativeBinarySpec context;
    private final LibraryBinaryLocator libraryBinaryLocator;

    public DefaultLibraryResolver(LibraryBinaryLocator libraryBinaryLocator, org.gradle.nativeplatform.NativeLibraryRequirement requirement, org.gradle.nativeplatform.NativeBinarySpec context) {
        this.requirement = requirement;
        this.context = context;
        this.libraryBinaryLocator = libraryBinaryLocator;
    }

    public org.gradle.nativeplatform.NativeLibraryBinary resolveLibraryBinary() {
        DomainObjectSet<org.gradle.nativeplatform.NativeLibraryBinary> binaries = libraryBinaryLocator.getBinaries(new LibraryIdentifier(requirement.getProjectPath(), requirement.getLibraryName()));
        if (binaries == null) {
            throw new LibraryResolveException(getFailureMessage(requirement));
        }
        return new LibraryResolution()
            .withFlavor(context.getFlavor())
            .withPlatform(context.getTargetPlatform())
            .withBuildType(context.getBuildType())
            .resolveLibrary(binaries);
    }

    private String getFailureMessage(org.gradle.nativeplatform.NativeLibraryRequirement requirement) {
        return requirement.getProjectPath() == null || requirement.getProjectPath().equals(context.getProjectPath())
            ? String.format("Could not locate library '%s' required by %s.", requirement.getLibraryName(), getContextMessage())
            : String.format("Could not locate library '%s' in project '%s' required by %s.", requirement.getLibraryName(), requirement.getProjectPath(), getContextMessage());
    }

    private String getContextMessage() {
        return String.format("'%s' in project '%s'", context.getComponent().getName(), context.getProjectPath());
    }

    private class LibraryResolution {
        private org.gradle.nativeplatform.Flavor flavor;
        private NativePlatform platform;
        private org.gradle.nativeplatform.BuildType buildType;

        public LibraryResolution withFlavor(org.gradle.nativeplatform.Flavor flavor) {
            this.flavor = flavor;
            return this;
        }

        public LibraryResolution withPlatform(NativePlatform platform) {
            this.platform = platform;
            return this;
        }

        public LibraryResolution withBuildType(org.gradle.nativeplatform.BuildType buildType) {
            this.buildType = buildType;
            return this;
        }

        public org.gradle.nativeplatform.NativeLibraryBinary resolveLibrary(DomainObjectSet<org.gradle.nativeplatform.NativeLibraryBinary> allBinaries) {
            Class<? extends org.gradle.nativeplatform.NativeLibraryBinary> type = getTypeForLinkage(requirement.getLinkage());
            DomainObjectSet<? extends org.gradle.nativeplatform.NativeLibraryBinary> candidateBinaries = allBinaries.withType(type);
            return resolve(candidateBinaries);
        }

        private Class<? extends org.gradle.nativeplatform.NativeLibraryBinary> getTypeForLinkage(String linkage) {
            if ("static".equals(linkage)) {
                return org.gradle.nativeplatform.StaticLibraryBinary.class;
            }
            if ("shared".equals(linkage) || linkage == null) {
                return org.gradle.nativeplatform.SharedLibraryBinary.class;
            }
            throw new InvalidUserDataException("Not a valid linkage: " + linkage);
        }

        private org.gradle.nativeplatform.NativeLibraryBinary resolve(Set<? extends org.gradle.nativeplatform.NativeLibraryBinary> candidates) {
            for (org.gradle.nativeplatform.NativeLibraryBinary candidate : candidates) {
                if (flavor != null && !flavor.getName().equals(candidate.getFlavor().getName())) {
                    continue;
                }
                if (platform != null && !platform.getName().equals(candidate.getTargetPlatform().getName())) {
                    continue;
                }
                if (buildType != null && !buildType.getName().equals(candidate.getBuildType().getName())) {
                    continue;
                }

                return candidate;
            }

            String typeName = GUtil.elvis(requirement.getLinkage(), "shared");
            throw new LibraryResolveException(String.format("No %s library binary available for library '%s' with [flavor: '%s', platform: '%s', buildType: '%s']",
                    typeName, requirement.getLibraryName(), flavor.getName(), platform.getName(), buildType.getName()));
        }
    }
}
