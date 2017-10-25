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

import net.rubygrapefruit.platform.MissingRegistryEntryException;
import net.rubygrapefruit.platform.WindowsRegistry;
import org.apache.commons.io.FileUtils;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.UncheckedException;
import org.gradle.util.VersionNumber;

import java.io.File;
import java.io.IOException;

public class DefaultVisualCppMetadataProvider implements VisualCppMetadataProvider {
    private static final String VS2017_METADATA_FILE_PATH = "VC/Auxiliary/Build/Microsoft.VCToolsVersion.default.txt";
    private static final String VS2017_COMPILER_PATH_PREFIX = "VC/Tools/MSVC";

    private static final String[] REGISTRY_BASEPATHS = {
        "SOFTWARE\\",
        "SOFTWARE\\Wow6432Node\\"
    };
    private static final String REGISTRY_ROOTPATH_VC = "Microsoft\\VisualStudio\\SxS\\VC7";

    private static final Logger LOGGER = Logging.getLogger(DefaultVisualCppMetadataProvider.class);

    private final WindowsRegistry windowsRegistry;

    public DefaultVisualCppMetadataProvider(WindowsRegistry windowsRegistry) {
        this.windowsRegistry = windowsRegistry;
    }

    @Override
    public VisualCppMetadata getVisualCppFromRegistry(String version) {
        for (String baseKey : REGISTRY_BASEPATHS) {
            try {
                File visualCppDir = new File(windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, baseKey + REGISTRY_ROOTPATH_VC, version));
                return new DefaultVisualCppMetadata(visualCppDir, VersionNumber.parse(version));
            } catch (MissingRegistryEntryException e) {
                // Version not found at this base path
            }
        }
        return null;
    }

    @Override
    public VisualCppMetadata getVisualCppFromMetadataFile(File installDir) {
        File msvcVersionFile = new File(installDir, VS2017_METADATA_FILE_PATH);
        if (!msvcVersionFile.exists() || !msvcVersionFile.isFile()) {
            LOGGER.debug("The MSVC version file at {} either does not exist or is not a file.  Cannot determine the MSVC version for this installation.", msvcVersionFile.getAbsolutePath());
            return null;
        }
        try {
            String versionString = FileUtils.readFileToString(msvcVersionFile).trim();
            File visualCppDir = new File(installDir, VS2017_COMPILER_PATH_PREFIX + "/" + versionString);
            return new DefaultVisualCppMetadata(visualCppDir, VersionNumber.parse(versionString));
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private static class DefaultVisualCppMetadata implements VisualCppMetadata {
        private final File visualCppDir;
        private final VersionNumber version;

        public DefaultVisualCppMetadata(File visualCppDir, VersionNumber version) {
            this.visualCppDir = visualCppDir;
            this.version = version;
        }

        @Override
        public File getVisualCppDir() {
            return visualCppDir;
        }

        @Override
        public VersionNumber getVersion() {
            return version;
        }
    }
}
