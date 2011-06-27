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
package org.gradle.util;

public abstract class OperatingSystem {
    public static OperatingSystem current() {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("windows")) {
            return new Windows();
        } else if (osName.contains("mac os x") || osName.contains("darwin")) {
            return new MacOs();
        } else if (osName.contains("sunos")) {
            return new Solaris();
        } else {
            // Not strictly true
            return new Unix();
        }
    }

    @Override
    public String toString() {
        return String.format("%s %s %s", System.getProperty("os.name"), System.getProperty("os.version"), System.getProperty("os.arch"));
    }

    public abstract boolean isWindows();

    public abstract boolean isUnix();

    public abstract boolean isCaseSensitiveFileSystem();

    public abstract String getNativePrefix();

    public abstract String getScriptName(String scriptPath);

    public static class Windows extends OperatingSystem {
        @Override
        public boolean isCaseSensitiveFileSystem() {
            return false;
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

    public static class Unix extends OperatingSystem {
        public String getScriptName(String scriptPath) {
            return scriptPath;
        }

        @Override
        public boolean isCaseSensitiveFileSystem() {
            return true;
        }

        @Override
        public boolean isWindows() {
            return false;
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
            String name = System.getProperty("os.name");
            String osPrefix = name.toLowerCase();
            int space = osPrefix.indexOf(" ");
            if (space != -1) {
                osPrefix = osPrefix.substring(0, space);
            }
            return osPrefix;
        }
    }

    public static class MacOs extends Unix {
        @Override
        public boolean isCaseSensitiveFileSystem() {
            return false;
        }

        @Override
        public String getNativePrefix() {
            return "darwin";
        }
    }

    public static class Solaris extends Unix {
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
