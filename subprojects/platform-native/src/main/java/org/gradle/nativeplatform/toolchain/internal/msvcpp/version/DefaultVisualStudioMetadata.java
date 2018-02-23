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

import org.gradle.util.VersionNumber;

import javax.annotation.Nullable;
import java.io.File;

public class DefaultVisualStudioMetadata implements VisualStudioMetadata {
    private final File installDir;
    private final File visualCppDir;
    private final VersionNumber version;
    private final VersionNumber visualCppVersion;
    private final Compatibility compatibility;

    DefaultVisualStudioMetadata(File installDir, @Nullable File visualCppDir, VersionNumber version, @Nullable VersionNumber visualCppVersion, Compatibility compatibility) {
        this.installDir = installDir;
        this.visualCppDir = visualCppDir;
        this.version = version;
        this.visualCppVersion = visualCppVersion;
        this.compatibility = compatibility;
    }

    @Override
    public File getInstallDir() {
        return installDir;
    }

    @Override
    public File getVisualCppDir() {
        return visualCppDir;
    }

    @Override
    public VersionNumber getVersion() {
        return version;
    }

    @Override
    public VersionNumber getVisualCppVersion() {
        return visualCppVersion;
    }

    @Override
    public Compatibility getCompatibility() {
        if (compatibility != null) {
            return compatibility;
        } else {
            if (version.getMajor() >= 15) {
                return Compatibility.VS2017_OR_LATER;
            } else {
                return Compatibility.LEGACY;
            }
        }
    }
}
