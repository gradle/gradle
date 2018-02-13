/*
 * Copyright 2018 the original author or authors.
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
import org.gradle.internal.os.OperatingSystem;

import java.io.File;

public class DefaultVswhereVersionLocator implements VswhereVersionLocator {
    private final WindowsRegistry windowsRegistry;
    private final OperatingSystem os;

    private static final String[] PROGRAM_FILES_KEYS = {
        "ProgramFilesDir",
        "ProgramFilesDir (x86)"
    };

    private static final String REGISTRY_PATH_WINDOWS = "SOFTWARE\\Microsoft\\Windows\\CurrentVersion";
    private static final String VISUAL_STUDIO_INSTALLER = "Microsoft Visual Studio/Installer";
    private static final String VSWHERE_EXE = "vswhere.exe";

    public DefaultVswhereVersionLocator(WindowsRegistry windowsRegistry, OperatingSystem os) {
        this.windowsRegistry = windowsRegistry;
        this.os = os;
    }

    @Override
    public File getVswhereInstall() {
        for (String programFilesKey : PROGRAM_FILES_KEYS) {
            File programFilesDir;
            try {
                programFilesDir = new File(windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, REGISTRY_PATH_WINDOWS, programFilesKey));
            } catch (MissingRegistryEntryException e) {
                // We'll get this when we try to look up "ProgramFilesDir (x86)" on a 32-bit OS
                continue;
            }

            File candidate = new File(programFilesDir, VISUAL_STUDIO_INSTALLER + "/" + VSWHERE_EXE);
            if (candidate.exists() && candidate.isFile()) {
                return candidate;
            }
        }

        return os.findInPath(VSWHERE_EXE);
    }
}
