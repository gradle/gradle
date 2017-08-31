/*
 * Copyright 2015 the original author or authors.
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

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.gradle.internal.FileUtils;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.util.TreeVisitor;
import org.gradle.util.VersionNumber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.rubygrapefruit.platform.MissingRegistryEntryException;
import net.rubygrapefruit.platform.WindowsRegistry;

public class DefaultUcrtLocator implements UcrtLocator {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultUcrtLocator.class);
    private static final String REGISTRY_BASEPATHS[] = {
        "SOFTWARE\\",
        "SOFTWARE\\Wow6432Node\\"
    };
    private static final String REGISTRY_ROOTPATH_KIT = "Microsoft\\Windows Kits\\Installed Roots";
    private static final String REGISTRY_KIT_10 = "KitsRoot10";
    private static final String VERSION_USER = "user";

    private static final String NAME_USER = "User-provided UCRT";
    private static final String NAME_KIT = "UCRT";

    private final Map<File, Set<Ucrt>> foundUcrts = new HashMap<File, Set<Ucrt>>();
    private final OperatingSystem os;
    private final WindowsRegistry windowsRegistry;
    private boolean initialised;

    public DefaultUcrtLocator(OperatingSystem os, WindowsRegistry windowsRegistry) {
        this.os = os;
        this.windowsRegistry = windowsRegistry;
    }

    public SearchResult locateUcrts(File candidate) {
        if (!initialised) {
            locateUcrtsInRegistry();
            initialised = true;
        }

        if (candidate != null) {
            return locateUserSpecifiedUcrt(candidate);
        }

        return locateDefaultUcrt();
    }

    private void locateUcrtsInRegistry() {
        for (String baseKey : REGISTRY_BASEPATHS) {
            locateUcrtsInRegistry(baseKey);
        }
    }

    private void locateUcrtsInRegistry(String baseKey) {
        String[] keys = {
                REGISTRY_KIT_10
        };

        for (String key : keys) {
            try {
                File ucrtDir = FileUtils.canonicalize(new File(windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, baseKey + REGISTRY_ROOTPATH_KIT, key)));
                String[] versionDirs = getUcrtVersionDirs(ucrtDir);
                if (versionDirs.length > 0) {
                    for (String versionDir : versionDirs) {
                        VersionNumber version = VersionNumber.withPatchNumber().parse(versionDir);
                        LOGGER.debug("Found ucrt {} at {}", version.toString(), ucrtDir);
                        putUcrt(new Ucrt(ucrtDir, NAME_KIT + " " + version.getMajor(), version));
                    }
                } else {
                    LOGGER.debug("Ignoring candidate ucrt directory {} as it does not look like a ucrt installation.", ucrtDir);
                }
            } catch (MissingRegistryEntryException e) {
                // Ignore the version if the string cannot be read
            }
        }
    }

    private SearchResult locateUserSpecifiedUcrt(File candidate) {
        File ucrtDir = FileUtils.canonicalize(candidate);
        String[] versionDirs = getUcrtVersionDirs(ucrtDir);
        if (versionDirs.length > 0) {
            for (String versionDir : versionDirs) {
                VersionNumber version = VersionNumber.withPatchNumber().parse(versionDir);
                LOGGER.debug("Found ucrt {} ({}) at {}", version.toString(), versionDir, ucrtDir);
                if (!foundUcrts.containsKey(ucrtDir)) {
                    putUcrt(new Ucrt(ucrtDir, NAME_USER, version));
                }
            }
            return new UcrtFound(getBestUcrt(candidate));
        } else {
            return new UcrtNotFound(String.format("The specified installation directory '%s' does not appear to contain a ucrt installation.", candidate));
        }
    }

    private SearchResult locateDefaultUcrt() {
        Ucrt selected = getBestUcrt();
        return selected == null
            ? new UcrtNotFound("Could not locate a ucrt installation using the Windows registry.")
            : new UcrtFound(selected);
    }

    private static String[] getUcrtVersionDirs(File candidate) {
        final Pattern ucrtVersionPattern = Pattern.compile("[0-9]+(\\.[0-9]+)*");
        FileFilter ucrtVersionFilter = new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                Matcher matcher = ucrtVersionPattern.matcher(pathname.getName());
                if (pathname.isDirectory() && matcher.matches()) {
                    return true;
                }
                return false;
            }
        };

        File includeDir = new File(candidate, "Include");
        File libDir = new File(candidate, "Lib");
        if (!includeDir.isDirectory() || !libDir.isDirectory()) {
            return new String[0];
        }

        Map<String, File> includeDirs = new HashMap<String, File>();
        for (File dir : includeDir.listFiles(ucrtVersionFilter)) {
            includeDirs.put(dir.getName(), dir);
        }
        Map<String, File> libDirs = new HashMap<String, File>();
        for (File dir : libDir.listFiles(ucrtVersionFilter)) {
            libDirs.put(dir.getName(), dir);
        }
        Set<String> ucrtVersions = new HashSet<String>();
        ucrtVersions.addAll(includeDirs.keySet());
        ucrtVersions.addAll(libDirs.keySet());

        List<String> result = new ArrayList<String>();
        for (String version : ucrtVersions) {
            File inc = includeDirs.get(version);
            File lib = includeDirs.get(version);
            if (inc != null && lib != null) {
                File ucrtInc = new File(inc, "ucrt");
                File ucrtLib = new File(lib, "ucrt");
                if (ucrtInc.isDirectory() && ucrtLib.isDirectory()) {
                    result.add(version);
                }
            }
        }
        return result.toArray(new String[result.size()]);
    }

    private void putUcrt(Ucrt ucrt) {
        Set<Ucrt> ucrts = foundUcrts.get(ucrt.getBaseDir());
        if (ucrts == null) {
            ucrts = new HashSet<Ucrt>();
            foundUcrts.put(ucrt.getBaseDir(), ucrts);
        }
        ucrts.add(ucrt);
    }

    private Ucrt getBestUcrt(File baseDir) {
        final SortedSet<Ucrt> candidates = new TreeSet<Ucrt>(new DescendingUcrtVersionComparator());
        Set<Ucrt> ucrts = foundUcrts.get(baseDir);
        if (ucrts != null) {
            candidates.addAll(ucrts);
        }
        return candidates.isEmpty() ? null : candidates.iterator().next();
    }

    private Ucrt getBestUcrt() {
        final SortedSet<Ucrt> candidates = new TreeSet<Ucrt>(new DescendingUcrtVersionComparator());
        for (Set<Ucrt> ucrts : foundUcrts.values()) {
            candidates.addAll(ucrts);
        }
        return candidates.isEmpty() ? null : candidates.iterator().next();
    }

    private static class UcrtFound implements SearchResult {
        private final Ucrt ucrt;

        public UcrtFound(Ucrt ucrt) {
            this.ucrt = ucrt;
        }

        public Ucrt getUcrt() {
            return ucrt;
        }

        public boolean isAvailable() {
            return true;
        }

        public void explain(TreeVisitor<? super String> visitor) {
        }
    }

    private static class UcrtNotFound implements SearchResult {
        private final String message;

        private UcrtNotFound(String message) {
            this.message = message;
        }

        public Ucrt getUcrt() {
            return null;
        }

        public boolean isAvailable() {
            return false;
        }

        public void explain(TreeVisitor<? super String> visitor) {
            visitor.node(message);
        }
    }

    private static class DescendingUcrtVersionComparator implements Comparator<Ucrt> {
        @Override
        public int compare(Ucrt o1, Ucrt o2) {
            return o2.getVersion().compareTo(o1.getVersion());
        }
    }
}
