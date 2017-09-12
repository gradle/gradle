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

package org.gradle.nativeplatform.toolchain.internal.msvcpp;

import net.rubygrapefruit.platform.WindowsRegistry;
import org.gradle.util.VersionNumber;

import java.io.File;

public class WindowsKitWindowsSdkLocator extends AbstractWindowsKitComponentLocator<WindowsKitWindowsSdk> {
    private static final String COMPONENT_NAME = "um";
    private static final String DISPLAY_NAME = "Windows SDK";
    private static final String RC_EXE = "rc.exe";

    public WindowsKitWindowsSdkLocator(WindowsRegistry windowsRegistry) {
        super(windowsRegistry);
    }

    @Override
    String getComponentName() {
        return COMPONENT_NAME;
    }

    @Override
    String getDisplayName() {
        return DISPLAY_NAME;
    }

    @Override
    boolean isValidComponentBaseDir(File baseDir) {
        for (String platform : PLATFORMS) {
            if (!new File(baseDir, "bin/" + platform + "/" + RC_EXE).exists()) {
                return false;
            }
        }
        return true;
    }

    @Override
    boolean isValidComponentIncludeDir(File includeDir) {
        return new File(includeDir, "windows.h").exists();
    }

    @Override
    boolean isValidComponentLibDir(File libDir) {
        for (String platform : PLATFORMS) {
            if (!new File(libDir, platform + "/kernel32.lib").exists()) {
                return false;
            }
        }
        return true;
    }

    @Override
    WindowsKitWindowsSdk newComponent(File baseDir, VersionNumber version, DiscoveryType discoveryType) {
        return new WindowsKitWindowsSdk(baseDir, version, getVersionedDisplayName(version, discoveryType));
    }
}
