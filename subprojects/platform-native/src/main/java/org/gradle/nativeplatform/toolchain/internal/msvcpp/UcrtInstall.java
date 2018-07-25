/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.nativeplatform.toolchain.internal.msvcpp;

import org.gradle.nativeplatform.platform.internal.NativePlatformInternal;
import org.gradle.nativeplatform.toolchain.internal.SystemLibraries;
import org.gradle.util.VersionNumber;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class UcrtInstall extends WindowsKitInstall {

    public UcrtInstall(File baseDir, VersionNumber version, String name) {
        super(baseDir, version, name);
    }

    /**
     * Returns the C runtime for the given platform.
     */
    public SystemLibraries getCRuntime(final NativePlatformInternal platform) {
        if (platform.getArchitecture().isAmd64()) {
            return new UcrtSystemLibraries("x64");
        }
        if (platform.getArchitecture().isArm()) {
            return new UcrtSystemLibraries("arm");
        }
        if (platform.getArchitecture().isI386()) {
            return new UcrtSystemLibraries("x86");
        }
        throw new UnsupportedOperationException(String.format("Supported %s for %s.", platform.getArchitecture().getDisplayName(), toString()));
    }

    private class UcrtSystemLibraries implements SystemLibraries {
        private final String platformDirName;

        UcrtSystemLibraries(String platformDirName) {
            this.platformDirName = platformDirName;
        }

        @Override
        public List<File> getIncludeDirs() {
            return Collections.singletonList(new File(getBaseDir(), "Include/" + getVersion() + "/ucrt"));
        }

        @Override
        public List<File> getLibDirs() {
            return Collections.singletonList(new File(getBaseDir(), "Lib/" + getVersion() + "/ucrt/" + platformDirName));
        }

        @Override
        public Map<String, String> getPreprocessorMacros() {
            return Collections.emptyMap();
        }
    }
}
