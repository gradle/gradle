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

package org.gradle.nativeplatform.toolchain.internal.msvcpp.version;

import org.gradle.api.specs.Spec;

import java.io.File;
import java.util.List;


public class VisualStudioVersionDeterminer implements VisualStudioMetaDataProvider {
    private final VisualStudioVersionLocator commandLineLocator;
    private final VisualStudioVersionLocator windowsRegistryLocator;
    private final VisualCppMetadataProvider visualCppMetadataProvider;

    public VisualStudioVersionDeterminer(VisualStudioVersionLocator commandLineLocator, VisualStudioVersionLocator windowsRegistryLocator, VisualCppMetadataProvider visualCppMetadataProvider) {
        this.commandLineLocator = commandLineLocator;
        this.windowsRegistryLocator = windowsRegistryLocator;
        this.visualCppMetadataProvider = visualCppMetadataProvider;
    }

    @Override
    public VisualStudioInstallCandidate getVisualStudioMetadataFromInstallDir(final File installDir) {
        // Check the normal metadata first
        VisualStudioInstallCandidate install = getVisualStudioMetadata(new Spec<VisualStudioInstallCandidate>() {
            @Override
            public boolean isSatisfiedBy(VisualStudioInstallCandidate install) {
                return install.getInstallDir().equals(installDir);
            }
        });

        // If we can't discover the version from the normal metadata, make some assumptions
        if (install == null) {
            VisualCppInstallCandidate visualCppMetadata = visualCppMetadataProvider.getVisualCppFromMetadataFile(installDir);
            if (visualCppMetadata != null) {
                return new VisualStudioMetadataBuilder()
                    .installDir(installDir)
                    .visualCppDir(visualCppMetadata.getVisualCppDir())
                    .visualCppVersion(visualCppMetadata.getVersion())
                    .compatibility(VisualStudioInstallCandidate.Compatibility.VS2017_OR_LATER)
                    .build();
            } else {
                File visualCppDir = new File(installDir, "VC");
                return new VisualStudioMetadataBuilder()
                    .installDir(installDir)
                    .visualCppDir(visualCppDir)
                    .compatibility(VisualStudioInstallCandidate.Compatibility.LEGACY)
                    .build();
            }
        }

        return install;
    }

    @Override
    public VisualStudioInstallCandidate getVisualStudioMetadataFromCompiler(final File compilerFile) {
        // Check the normal metadata first
        VisualStudioInstallCandidate install = getVisualStudioMetadata(new Spec<VisualStudioInstallCandidate>() {
            @Override
            public boolean isSatisfiedBy(VisualStudioInstallCandidate install) {
                if (install.getVersion().getMajor() >= 15) {
                    File compilerRoot = getNthParent(compilerFile, 4);
                    return compilerRoot.equals(install.getVisualCppDir());
                } else {
                    File compilerRoot = getNthParent(compilerFile, 2);
                    if (compilerRoot.equals(install.getVisualCppDir())) {
                        return true;
                    } else {
                        compilerRoot = getNthParent(compilerFile, 3);
                        return compilerRoot.equals(install.getVisualCppDir());
                    }
                }
            }
        });

        // If we can't discover the version from the normal metadata, make some assumptions
        if (install == null) {
            File installDir = getNthParent(compilerFile, 8);
            VisualCppInstallCandidate visualCppMetadata = visualCppMetadataProvider.getVisualCppFromMetadataFile(installDir);
            if (visualCppMetadata != null) {
                File visualCppDir = visualCppMetadata.getVisualCppDir();
                return new VisualStudioMetadataBuilder()
                    .installDir(installDir)
                    .visualCppDir(visualCppDir)
                    .visualCppVersion(visualCppMetadata.getVersion())
                    .compatibility(VisualStudioInstallCandidate.Compatibility.VS2017_OR_LATER)
                    .build();
            } else {
                File visualCppDir = getNthParent(compilerFile, 2);
                if (!"VC".equals(visualCppDir.getName()))  {
                    visualCppDir = getNthParent(compilerFile, 3);
                }
                return new VisualStudioMetadataBuilder()
                    .installDir(visualCppDir.getParentFile())
                    .visualCppDir(visualCppDir)
                    .compatibility(VisualStudioInstallCandidate.Compatibility.LEGACY)
                    .build();
            }
        }

        return install;
    }

    private VisualStudioInstallCandidate getVisualStudioMetadata(Spec<VisualStudioInstallCandidate> spec) {
        List<VisualStudioInstallCandidate> installs = commandLineLocator.getVisualStudioInstalls();
        if (installs.size() > 0) {
            VisualStudioInstallCandidate install = findMetadataForInstallDir(spec, installs);
            if (install != null) {
                return install;
            }
        } else {
            installs = windowsRegistryLocator.getVisualStudioInstalls();
            VisualStudioInstallCandidate install = findMetadataForInstallDir(spec, installs);
            if (install != null) {
                return install;
            }
        }

        return null;
    }

    private VisualStudioInstallCandidate findMetadataForInstallDir(Spec<VisualStudioInstallCandidate> spec, List<VisualStudioInstallCandidate> installs) {
        for (VisualStudioInstallCandidate install : installs) {
            if (spec.isSatisfiedBy(install)) {
                return install;
            }
        }
        return null;
    }

    private static File getNthParent(File file, int n) {
        if (n == 0) {
            return file;
        } else {
            File parent = file.getParentFile();
            if (parent != null) {
                return getNthParent(parent, --n);
            } else {
                return file;
            }
        }
    }
}
