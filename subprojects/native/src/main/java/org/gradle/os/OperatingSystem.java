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
package org.gradle.os;

public abstract class OperatingSystem {
    private static final Windows WINDOWS = new Windows();
    private static final MacOs MAC_OS = new MacOs();
    private static final Solaris SOLARIS = new Solaris();
    private static final Unix UNIX = new Unix();

    public static OperatingSystem current() {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("windows")) {
            return WINDOWS;
        } else if (osName.contains("mac os x") || osName.contains("darwin")) {
            return MAC_OS;
        } else if (osName.contains("sunos")) {
            return SOLARIS;
        } else {
            // Not strictly true
            return UNIX;
        }
    }

    @Override
    public String toString() {
        return String.format("%s %s %s", System.getProperty("os.name"), System.getProperty("os.version"), System.getProperty("os.arch"));
    }

    public abstract boolean isWindows();

    public abstract boolean isUnix();

    public abstract boolean isMacOsX();

    public abstract FileSystem getFileSystem();

    public abstract String getNativePrefix();

    public abstract String getScriptName(String scriptPath);

    static class Windows extends OperatingSystem {
        private static final FileSystem FILE_SYSTEM = new WindowsFileSystem();

        @Override
        public FileSystem getFileSystem() {
            return FILE_SYSTEM;
        }

        @Override
        public boolean isWindows() {
            return true;
        }

        @Override
        public boolean isUnix() {
            return false;
        }

        @Override
        public boolean isMacOsX() {
            return false;
        }

        @Override
        public String getScriptName(String scriptPath) {
            if (scriptPath.toLowerCase().endsWith(".bat")) {
                return scriptPath;
            }
            return scriptPath + ".bat";
        }

        @Override
        public String getNativePrefix() {
            String arch = System.getProperty("os.arch");
            if ("i386".equals(arch)) {
                arch = "x86";
            }
            return "win32-" + arch;
        }
    }

    static class Unix extends OperatingSystem {
        private static final FileSystem FILE_SYSTEM = new UnixFileSystem();

        public String getScriptName(String scriptPath) {
            return scriptPath;
        }

        @Override
        public FileSystem getFileSystem() {
            return FILE_SYSTEM;
        }

        @Override
        public boolean isWindows() {
            return false;
        }

        @Override
        public boolean isUnix() {
            return true;
        }

        @Override
        public boolean isMacOsX() {
            return false;
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
            String name = System.getProperty("os.name");
            String osPrefix = name.toLowerCase();
            int space = osPrefix.indexOf(" ");
            if (space != -1) {
                osPrefix = osPrefix.substring(0, space);
            }
            return osPrefix;
        }
    }

    static class MacOs extends Unix {
        private static final FileSystem FILE_SYSTEM = new MacFileSystem();

        @Override
        public FileSystem getFileSystem() {
            return FILE_SYSTEM;
        }

        @Override
        public boolean isMacOsX() {
            return true;

        }

        @Override
        public String getNativePrefix() {
            return "darwin";
        }
    }

    static class Solaris extends Unix {
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

    static class UnixFileSystem implements FileSystem {
        public boolean isCaseSensitive() {
            return true;
        }

        public boolean isSymlinkAware() {
            return true;
        }

        public boolean getImplicitlyLocksFileOnOpen() {
            return false;
        }
    }

    static class MacFileSystem extends UnixFileSystem {
        @Override
        public boolean isCaseSensitive() {
            return false;
        }
    }

    static class WindowsFileSystem implements FileSystem {
        public boolean isCaseSensitive() {
            return false;
        }

        public boolean isSymlinkAware() {
            // Not strictly true - Vista and later can handle symlinks. But not every user (most users?) don't have permission to create them.
            return false;
        }

        public boolean getImplicitlyLocksFileOnOpen() {
            return true;
        }
    }
}
