/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.platform;

import org.gradle.internal.os.OperatingSystem;
import org.jspecify.annotations.Nullable;

import java.io.File;

import static org.gradle.internal.FileUtils.withExtension;

public class PlatformBinaryResolver {

    private static final PlatformBinaryResolver CURRENT_OS_INSTANCE = new PlatformBinaryResolver(OperatingSystem.current());

    private final OperatingSystem os;

    private PlatformBinaryResolver(OperatingSystem os) {
        this.os = os;
    }

    public String getScriptName(String scriptPath) {
        if (os.isWindows()) {
            return withExtension(scriptPath, ".bat");
        }
        return scriptPath;
    }

    public String getExecutableName(String executablePath) {
        if (os.isWindows()) {
            return withExtension(executablePath, ".exe");
        }
        return executablePath;
    }

    public String getExecutableSuffix() {
        if (os.isWindows()) {
            return ".exe";
        }
        return "";
    }

    public String getSharedLibraryName(String libraryName) {
        if (os.isWindows()) {
            return withExtension(libraryName, ".dll");
        }
        return getUnixLibraryName(libraryName, getSharedLibrarySuffix());
    }

    public String getSharedLibrarySuffix() {
        if (os.isWindows()) {
            return ".dll";
        }
        if (os.isMacOsX()) {
            return ".dylib";
        }
        return ".so";
    }

    public String getStaticLibraryName(String libraryName) {
        if (os.isWindows()) {
            return withExtension(libraryName, ".lib");
        }
        return getUnixLibraryName(libraryName, ".a");
    }

    public String getStaticLibrarySuffix() {
        if (os.isWindows()) {
            return ".lib";
        }
        return ".a";
    }

    public String getLinkLibrarySuffix() {
        if (os.isWindows()) {
            return ".lib";
        }
        return getSharedLibrarySuffix();
    }

    public String getLinkLibraryName(String libraryPath) {
        if (os.isWindows()) {
            return withExtension(libraryPath, ".lib");
        }
        return getSharedLibraryName(libraryPath);
    }

    /**
     * Locates the given executable in the system path. Returns null if not found.
     */
    @Nullable
    public File findExecutableInPath(String name) {
        String exeName = getExecutableName(name);
        if (exeName.contains(File.separator)) {
            File candidate = new File(exeName);
            if (candidate.isFile()) {
                return candidate;
            }
            return null;
        }
        for (File dir : os.getPath()) {
            File candidate = new File(dir, exeName);
            if (candidate.isFile()) {
                return candidate;
            }
        }
        return null;
    }

    private String getUnixLibraryName(String libraryName, String suffix) {
        if (libraryName.endsWith(suffix)) {
            return libraryName;
        }
        int pos = libraryName.lastIndexOf('/');
        if (pos >= 0) {
            return libraryName.substring(0, pos + 1) + "lib" + libraryName.substring(pos + 1) + suffix;
        } else {
            return "lib" + libraryName + suffix;
        }
    }

    public static PlatformBinaryResolver forOs(OperatingSystem os) {
        if (os == OperatingSystem.current()) {
            return forCurrentOs();
        }
        return new PlatformBinaryResolver(os);
    }

    public static PlatformBinaryResolver forCurrentOs() {
        return CURRENT_OS_INSTANCE;
    }
}
