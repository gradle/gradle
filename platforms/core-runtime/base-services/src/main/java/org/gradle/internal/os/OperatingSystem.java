/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.internal.os;

import org.gradle.internal.scan.UsedByScanPlugin;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import static org.gradle.internal.FileUtils.withExtension;


@SuppressWarnings("ClassInitializationDeadlock")
public abstract class OperatingSystem {
    public static final OperatingSystem WINDOWS = new Windows();
    public static final OperatingSystem MAC_OS = new MacOs();
    public static final OperatingSystem SOLARIS = new Solaris();
    public static final OperatingSystem LINUX = new Linux();
    public static final OperatingSystem FREE_BSD = new FreeBSD();
    public static final OperatingSystem UNIX = new Unix();
    public static final OperatingSystem AIX = new Aix();
    private static OperatingSystem currentOs;
    private final String toStringValue;
    private final String osName;
    private final String osVersion;

    OperatingSystem() {
        osName = System.getProperty("os.name");
        osVersion = System.getProperty("os.version");
        toStringValue = getName() + " " + getVersion() + " " + System.getProperty("os.arch");
    }

    public static OperatingSystem current() {
        if (currentOs == null) {
            currentOs = forName(System.getProperty("os.name"));
        }
        return currentOs;
    }

    // for testing current()
    static void resetCurrent() {
        currentOs = null;
    }

    public static OperatingSystem forName(String os) {
        String osName = os.toLowerCase(Locale.ROOT);
        if (osName.contains("windows")) {
            return WINDOWS;
        } else if (osName.contains("mac os x") || osName.contains("darwin") || osName.contains("osx")) {
            return MAC_OS;
        } else if (osName.contains("sunos") || osName.contains("solaris")) {
            return SOLARIS;
        } else if (osName.contains("linux")) {
            return LINUX;
        } else if (osName.contains("freebsd")) {
            return FREE_BSD;
        } else if (osName.contains("aix")) {
            return AIX;
        } else {
            // Not strictly true
            return UNIX;
        }
    }

    @Override
    public String toString() {
        return toStringValue;
    }

    public String getName() {
        return osName;
    }

    public String getVersion() {
        return osVersion;
    }

    @UsedByScanPlugin
    public boolean isWindows() {
        return false;
    }

    public boolean isUnix() {
        return false;
    }

    public boolean isMacOsX() {
        return false;
    }

    public boolean isLinux() {
        return false;
    }

    public abstract String getNativePrefix();

    public abstract String getScriptName(String scriptPath);

    public abstract String getExecutableName(String executablePath);

    public abstract String getExecutableSuffix();

    public abstract String getSharedLibraryName(String libraryName);

    public abstract String getSharedLibrarySuffix();

    public abstract String getStaticLibraryName(String libraryName);

    public abstract String getStaticLibrarySuffix();

    public abstract String getLinkLibrarySuffix();

    public abstract String getLinkLibraryName(String libraryPath);

    @UsedByScanPlugin
    public abstract String getFamilyName();

    /**
     * Locates the given executable in the system path. Returns null if not found.
     */
    @Nullable
    public File findInPath(String name) {
        String exeName = getExecutableName(name);
        if (exeName.contains(File.separator)) {
            File candidate = new File(exeName);
            if (candidate.isFile()) {
                return candidate;
            }
            return null;
        }
        for (File dir : getPath()) {
            File candidate = new File(dir, exeName);
            if (candidate.isFile()) {
                return candidate;
            }
        }

        return null;
    }

    public List<File> findAllInPath(String name) {
        List<File> all = new LinkedList<File>();

        for (File dir : getPath()) {
            File candidate = new File(dir, name);
            if (candidate.isFile()) {
                all.add(candidate);
            }
        }

        return all;
    }

    @SuppressWarnings("MixedMutabilityReturnType")
    public List<File> getPath() {
        String path = System.getenv(getPathVar());
        if (path == null) {
            return Collections.emptyList();
        }
        List<File> entries = new ArrayList<File>();
        for (String entry : path.split(Pattern.quote(File.pathSeparator))) {
            entries.add(new File(entry));
        }
        return entries;
    }

    public String getPathVar() {
        return "PATH";
    }

    private static class Windows extends OperatingSystem {
        private final String nativePrefix;

        Windows() {
            nativePrefix = resolveNativePrefix();
        }

        @Override
        public boolean isWindows() {
            return true;
        }

        @Override
        public String getFamilyName() {
            return "windows";
        }

        @Override
        public String getScriptName(String scriptPath) {
            return withExtension(scriptPath, ".bat");
        }

        @Override
        public String getExecutableSuffix() {
            return ".exe";
        }

        @Override
        public String getExecutableName(String executablePath) {
            return withExtension(executablePath, ".exe");
        }

        @Override
        public String getSharedLibrarySuffix() {
            return ".dll";
        }

        @Override
        public String getSharedLibraryName(String libraryPath) {
            return withExtension(libraryPath, ".dll");
        }

        @Override
        public String getLinkLibrarySuffix() {
            return ".lib";
        }

        @Override
        public String getLinkLibraryName(String libraryPath) {
            return withExtension(libraryPath, ".lib");
        }

        @Override
        public String getStaticLibrarySuffix() {
            return ".lib";
        }

        @Override
        public String getStaticLibraryName(String libraryName) {
            return withExtension(libraryName, ".lib");
        }

        @Override
        public String getNativePrefix() {
            return nativePrefix;
        }

        private String resolveNativePrefix() {
            String arch = System.getProperty("os.arch");
            if ("i386".equals(arch)) {
                arch = "x86";
            }
            return "win32-" + arch;
        }

        @Override
        public String getPathVar() {
            return "Path";
        }
    }

    private static class Unix extends OperatingSystem {
        private final String nativePrefix;

        Unix() {
            this.nativePrefix = resolveNativePrefix();
        }

        @Override
        public String getScriptName(String scriptPath) {
            return scriptPath;
        }

        @Override
        public String getFamilyName() {
            return "unknown";
        }

        @Override
        public String getExecutableSuffix() {
            return "";
        }

        @Override
        public String getExecutableName(String executablePath) {
            return executablePath;
        }

        @Override
        public String getSharedLibraryName(String libraryName) {
            return getLibraryName(libraryName, getSharedLibrarySuffix());
        }

        private String getLibraryName(String libraryName, String suffix) {
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

        @Override
        public String getSharedLibrarySuffix() {
            return ".so";
        }

        @Override
        public String getLinkLibrarySuffix() {
            return getSharedLibrarySuffix();
        }

        @Override
        public String getLinkLibraryName(String libraryPath) {
            return getSharedLibraryName(libraryPath);
        }

        @Override
        public String getStaticLibrarySuffix() {
            return ".a";
        }

        @Override
        public String getStaticLibraryName(String libraryName) {
            return getLibraryName(libraryName, ".a");
        }

        @Override
        public boolean isUnix() {
            return true;
        }

        @Override
        public String getNativePrefix() {
            return nativePrefix;
        }

        private String resolveNativePrefix() {
            String arch = getArch();
            String osPrefix = getOsPrefix();
            osPrefix += "-" + arch;
            return osPrefix;
        }

        protected String getArch() {
            String arch = System.getProperty("os.arch");
            if ("x86".equals(arch)) {
                arch = "i386";
            }
            if ("x86_64".equals(arch)) {
                arch = "amd64";
            }
            if ("powerpc".equals(arch)) {
                arch = "ppc";
            }
            return arch;
        }

        protected String getOsPrefix() {
            String osPrefix = getName().toLowerCase(Locale.ROOT);
            int space = osPrefix.indexOf(" ");
            if (space != -1) {
                osPrefix = osPrefix.substring(0, space);
            }
            return osPrefix;
        }
    }

    private static class MacOs extends Unix {
        @Override
        public boolean isMacOsX() {
            return true;
        }

        @Override
        public String getFamilyName() {
            return "os x";
        }

        @Override
        public String getSharedLibrarySuffix() {
            return ".dylib";
        }

        @Override
        public String getNativePrefix() {
            return "darwin";
        }
    }

    private static class Linux extends Unix {
        @Override
        public boolean isLinux() {
            return true;
        }

        @Override
        public String getFamilyName() {
            return "linux";
        }
    }

    private static class FreeBSD extends Unix {
    }

    private static class Solaris extends Unix {
        @Override
        public String getFamilyName() {
            return "solaris";
        }

        @Override
        protected String getOsPrefix() {
            return "sunos";
        }

        @Override
        protected String getArch() {
            String arch = System.getProperty("os.arch");
            if (arch.equals("i386") || arch.equals("x86")) {
                return "x86";
            }
            return super.getArch();
        }
    }

    private static class Aix extends Unix {
        @Override
        public String getFamilyName() {
            return "aix";
        }

        @Override
        protected String getOsPrefix() {
            return "aix";
        }
    }

}
