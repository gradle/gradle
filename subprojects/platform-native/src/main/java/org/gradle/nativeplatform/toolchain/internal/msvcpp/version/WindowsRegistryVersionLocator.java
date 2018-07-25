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

import com.google.common.collect.Lists;
import net.rubygrapefruit.platform.MissingRegistryEntryException;
import net.rubygrapefruit.platform.WindowsRegistry;
import org.gradle.internal.FileUtils;
import org.gradle.util.VersionNumber;

import java.io.File;
import java.util.List;

public class WindowsRegistryVersionLocator extends AbstractVisualStudioVersionLocator implements VisualStudioVersionLocator {
    static final String[] REGISTRY_BASEPATHS = {
        "SOFTWARE\\",
        "SOFTWARE\\Wow6432Node\\"
    };
    static final String REGISTRY_ROOTPATH_VC = "Microsoft\\VisualStudio\\SxS\\VC7";

    private final WindowsRegistry windowsRegistry;

    public WindowsRegistryVersionLocator(WindowsRegistry windowsRegistry) {
        this.windowsRegistry = windowsRegistry;
    }

    @Override
    protected List<VisualStudioInstallCandidate> locateInstalls() {
        List<VisualStudioInstallCandidate> installs = Lists.newArrayList();
        for (String baseKey : REGISTRY_BASEPATHS) {
            locateInstallsInRegistry(installs, baseKey);
        }
        return installs;
    }

    @Override
    public String getSource() {
        return "windows registry";
    }

    private void locateInstallsInRegistry(List<VisualStudioInstallCandidate> installs, String baseKey) {
        List<String> visualCppVersions;
        try {
            visualCppVersions = windowsRegistry.getValueNames(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, baseKey + REGISTRY_ROOTPATH_VC);
        } catch (MissingRegistryEntryException e) {
            // No Visual Studio information available in the registry
            return;
        }

        for (String versionString : visualCppVersions) {
            if (!versionString.matches("\\d+\\.\\d+")) {
                // Ignore the other values
                continue;
            }
            File visualCppDir = new File(windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, baseKey + REGISTRY_ROOTPATH_VC, versionString));
            visualCppDir = FileUtils.canonicalize(visualCppDir);
            File visualStudioDir = visualCppDir.getParentFile();
            VersionNumber version = VersionNumber.parse(versionString);
            installs.add(new VisualStudioMetadataBuilder()
                .installDir(visualStudioDir)
                .visualCppDir(visualCppDir)
                .version(version)
                .visualCppVersion(version)
                .build());
        }
    }
}
