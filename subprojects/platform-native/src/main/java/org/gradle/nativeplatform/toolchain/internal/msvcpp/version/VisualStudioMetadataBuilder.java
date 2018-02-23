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

import org.gradle.internal.FileUtils;
import org.gradle.util.VersionNumber;

import java.io.File;

public class VisualStudioMetadataBuilder {
    private File installDir;
    private File visualCppDir;
    private VersionNumber version = VersionNumber.UNKNOWN;
    private VersionNumber visualCppVersion = VersionNumber.UNKNOWN;
    private VisualStudioMetadata.Compatibility compatibility;

    public VisualStudioMetadataBuilder installDir(File installDir) {
        this.installDir = FileUtils.canonicalize(installDir);
        return this;
    }

    public VisualStudioMetadataBuilder visualCppDir(File visualCppDir) {
        this.visualCppDir = FileUtils.canonicalize(visualCppDir);
        return this;
    }

    public VisualStudioMetadataBuilder version(VersionNumber version) {
        this.version = version;
        return this;
    }

    public VisualStudioMetadataBuilder visualCppVersion(VersionNumber version) {
        this.visualCppVersion = version;
        return this;
    }

    public VisualStudioMetadataBuilder compatibility(VisualStudioMetadata.Compatibility compatibility) {
        this.compatibility = compatibility;
        return this;
    }

    public VisualStudioMetadata build() {
        return new DefaultVisualStudioMetadata(installDir, visualCppDir, version, visualCppVersion, compatibility);
    }
}
