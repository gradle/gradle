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

import org.gradle.api.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

public abstract class OperatingSystem {
    public static final Windows WINDOWS = new Windows();
    public static final MacOs MAC_OS = new MacOs();
    public static final Solaris SOLARIS = new Solaris();
    public static final Linux LINUX = new Linux();
    public static final FreeBSD FREE_BSD = new FreeBSD();
    public static final Unix UNIX = new Unix();

    public static OperatingSystem current() {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("windows")) {
            return WINDOWS;
        } else if (osName.contains("mac os x") || osName.contains("darwin")) {
            return MAC_OS;
        } else if (osName.contains("sunos")) {
            return SOLARIS;
        } else if (osName.contains("linux")) {
            return LINUX;
        } else if (osName.contains("freebsd")) {
            return FREE_BSD;
        } else {
            // Not strictly true
            return UNIX;
        }
    }

    @Override
    public String toString() {
        return String.format("%s %s %s", getName(), getVersion(), System.getProperty("os.arch"));
    }

    public String getName() {
        return System.getProperty("os.name");
    }
    
    public String getVersion() {
        return System.getProperty("os.version");
    }

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

    public abstract String getSharedLibraryName(String libraryName);

    public abstract String getStaticLibraryName(String libraryName);

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

    static class Windows extends OperatingSystem {
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
            return withSuffix(scriptPath, ".bat");
        }

        @Override
        public String getExecutableName(String executablePath) {
            return withSuffix(executablePath, ".exe");
        }

        @Override
        public String getSharedLibraryName(String libraryPath) {
            return withSuffix(libraryPath, ".dll");
        }

        @Override
        public String getStaticLibraryName(String libraryName) {
            return withSuffix(libraryName, ".lib");
        }

        @Override
        public String getNativePrefix() {
            String arch = System.getProperty("os.arch");
            if ("i386".equals(arch)) {
                arch = "x86";
            }
            return "win32-" + arch;
        }

        private String withSuffix(String executablePath, String extension) {
            if (executablePath.toLowerCase().endsWith(extension)) {
                return executablePath;
            }
            return removeExtension(executablePath) + extension;
        }

        private String removeExtension(String executablePath) {
            int fileNameStart = Math.max(executablePath.lastIndexOf('/'), executablePath.lastIndexOf('\\'));
            int extensionPos = executablePath.lastIndexOf('.');

            if (extensionPos > fileNameStart) {
                return executablePath.substring(0, extensionPos);
            }
            return executablePath;
        }


        @Override
        public String getPathVar() {
            return "Path";
        }
    }

    static class Unix extends OperatingSystem {
        @Override
        public String getScriptName(String scriptPath) {
            return scriptPath;
        }

        @Override
        public String getFamilyName() {
            return "unknown";
        }

        @Override
        public String getExecutableName(String executablePath) {
            return executablePath;
        }

        @Override
        public String getSharedLibraryName(String libraryName) {
            return getLibraryName(libraryName, getSharedLibSuffix());
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

        protected String getSharedLibSuffix() {
            return ".so";
        }

        @Override
        public String getStaticLibraryName(String libraryName) {
            return getLibraryName(libraryName, ".a");
        }

        @Override
        public boolean isUnix() {
            return true;
        }

        public String getNativePrefix() {
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
            String osPrefix = getName().toLowerCase();
            int space = osPrefix.indexOf(" ");
            if (space != -1) {
                osPrefix = osPrefix.substring(0, space);
            }
            return osPrefix;
        }
    }

    static class MacOs extends Unix {
        @Override
        public boolean isMacOsX() {
            return true;

        }

        @Override
        public String getFamilyName() {
            return "os x";
        }

        @Override
        protected String getSharedLibSuffix() {
            return ".dylib";
        }

        @Override
        public String getNativePrefix() {
            return "darwin";
        }
    }

    static class Linux extends Unix {
        @Override
        public boolean isLinux() {
            return true;
        }

        @Override
        public String getFamilyName() {
            return "linux";
        }
    }

    static class FreeBSD extends Unix {
    }

    static class Solaris extends Unix {
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
}
