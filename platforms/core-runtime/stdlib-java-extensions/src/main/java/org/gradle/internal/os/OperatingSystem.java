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
package org.gradle.internal.os;

import org.gradle.internal.scan.UsedByScanPlugin;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@SuppressWarnings("ClassInitializationDeadlock")
public abstract class OperatingSystem {
    public static final Windows WINDOWS = new Windows();
    public static final MacOs MAC_OS = new MacOs();
    public static final Solaris SOLARIS = new Solaris();
    public static final Linux LINUX = new Linux();
    public static final FreeBSD FREE_BSD = new FreeBSD();
    public static final Unix UNIX = new Unix();
    private static @Nullable OperatingSystem currentOs;
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

    @UsedByScanPlugin
    public abstract String getFamilyName();

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

    static class Windows extends OperatingSystem {
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

    static class Unix extends OperatingSystem {
        private final String nativePrefix;

        Unix() {
            this.nativePrefix = resolveNativePrefix();
        }

        @Override
        public String getFamilyName() {
            return "unknown";
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
