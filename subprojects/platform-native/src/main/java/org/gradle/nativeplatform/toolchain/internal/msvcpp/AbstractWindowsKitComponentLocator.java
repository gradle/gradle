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

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.SetMultimap;
import net.rubygrapefruit.platform.MissingRegistryEntryException;
import net.rubygrapefruit.platform.WindowsRegistry;
import org.gradle.api.Transformer;
import org.gradle.internal.FileUtils;
import org.gradle.util.CollectionUtils;
import org.gradle.util.VersionNumber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class AbstractWindowsKitComponentLocator<T extends WindowsKitComponent> implements WindowsComponentLocator<T> {
    private static final String USER_PROVIDED = "User-provided";
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractWindowsKitComponentLocator.class);
    private final SetMultimap<File, T> foundComponents = HashMultimap.create();
    private final Set<File> brokenComponents = new LinkedHashSet<File>();
    private final WindowsRegistry windowsRegistry;
    private boolean initialised;

    protected enum DiscoveryType {REGISTRY, USER}

    private static final String[] REGISTRY_BASEPATHS = {
        "SOFTWARE\\",
        "SOFTWARE\\Wow6432Node\\"
    };
    private static final String REGISTRY_ROOTPATH_KIT = "Microsoft\\Windows Kits\\Installed Roots";
    private static final String REGISTRY_KIT_10 = "KitsRoot10";

    private final Pattern windowsKitVersionPattern = Pattern.compile("[0-9]+(\\.[0-9]+)*");
    private final FileFilter windowsKitVersionFilter = new FileFilter() {
        @Override
        public boolean accept(File pathname) {
            Matcher matcher = windowsKitVersionPattern.matcher(pathname.getName());
            return pathname.isDirectory() && matcher.matches();
        }
    };

    AbstractWindowsKitComponentLocator(WindowsRegistry windowsRegistry) {
        this.windowsRegistry = windowsRegistry;
    }

    @Override
    public SearchResult<T> locateComponent(File candidate) {
        initializeComponents();

        if (candidate != null) {
            return locateUserSpecifiedComponent(candidate);
        }

        return locateDefaultComponent();
    }

    @Override
    public List<T> locateAllComponents() {
        initializeComponents();

        return Lists.newArrayList(foundComponents.values());
    }

    private void initializeComponents() {
        if (!initialised) {
            locateComponentsInRegistry();
            initialised = true;
        }
    }

    private SearchResult<T> locateDefaultComponent() {
        T selected = getBestComponent();
        if (selected != null) {
            return new ComponentFound<T>(selected);
        }
        if (brokenComponents.isEmpty()) {
            return new ComponentNotFound<T>("Could not locate a " + getDisplayName() + " installation using the Windows registry.");
        }
        return new ComponentNotFound<T>("Could not locate a " + getDisplayName() + " installation. None of the following locations contain a valid installation",
            CollectionUtils.collect(brokenComponents, new Transformer<String, File>() {
                @Override
                public String transform(File file) {
                    return file.getAbsolutePath();
                }
            }));
    }

    private T getBestComponent(File baseDir) {
        final SortedSet<T> candidates = new TreeSet<T>(new DescendingComponentVersionComparator());
        Set<T> components = foundComponents.get(baseDir);
        if (components != null) {
            candidates.addAll(components);
        }
        return candidates.isEmpty() ? null : candidates.iterator().next();
    }

    private T getBestComponent() {
        final SortedSet<T> candidates = new TreeSet<T>(new DescendingComponentVersionComparator());
        candidates.addAll(foundComponents.values());
        return candidates.isEmpty() ? null : candidates.iterator().next();
    }

    private void locateComponentsInRegistry() {
        for (String baseKey : REGISTRY_BASEPATHS) {
            locateComponentsInRegistry(baseKey);
        }
    }

    private void locateComponentsInRegistry(String baseKey) {
        try {
            File windowsKitDir = FileUtils.canonicalize(new File(windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, baseKey + REGISTRY_ROOTPATH_KIT, REGISTRY_KIT_10)));
            String[] versionDirs = getComponentVersionDirs(windowsKitDir);
            if (isValidComponentBaseDir(windowsKitDir) && versionDirs.length > 0) {
                for (String versionDir : versionDirs) {
                    VersionNumber version = VersionNumber.withPatchNumber().parse(versionDir);
                    LOGGER.debug("Found {} {} at {}", getDisplayName(), version.toString(), windowsKitDir);
                    putComponent(newComponent(windowsKitDir, version, DiscoveryType.REGISTRY));
                }
            } else {
                brokenComponents.add(windowsKitDir);
                LOGGER.debug("Ignoring candidate directory {} as it does not look like a {} installation.", windowsKitDir, getDisplayName());
            }
        } catch (MissingRegistryEntryException e) {
            // Ignore the version if the string cannot be read
        }
    }

    private SearchResult<T> locateUserSpecifiedComponent(File candidate) {
        File windowsKitDir = FileUtils.canonicalize(candidate);
        String[] versionDirs = getComponentVersionDirs(windowsKitDir);
        if (isValidComponentBaseDir(windowsKitDir) && versionDirs.length > 0) {
            for (String versionDir : versionDirs) {
                VersionNumber version = VersionNumber.withPatchNumber().parse(versionDir);
                LOGGER.debug("Found {} {} ({}) at {}", getDisplayName(), version.toString(), versionDir, windowsKitDir);
                if (!foundComponents.containsKey(windowsKitDir)) {
                    putComponent(newComponent(windowsKitDir, version, DiscoveryType.USER));
                }
            }
            return new ComponentFound<T>(getBestComponent(candidate));
        } else {
            return new ComponentNotFound<T>(String.format("The specified installation directory '%s' does not appear to contain a %s installation.", candidate, getDisplayName()));
        }
    }

    private String[] getComponentVersionDirs(File candidate) {
        File includeDir = new File(candidate, "Include");
        File libDir = new File(candidate, "Lib");
        if (!includeDir.isDirectory() || !libDir.isDirectory()) {
            return new String[0];
        }

        Map<String, File> includeDirs = new HashMap<String, File>();
        for (File dir : includeDir.listFiles(windowsKitVersionFilter)) {
            includeDirs.put(dir.getName(), dir);
        }
        Map<String, File> libDirs = new HashMap<String, File>();
        for (File dir : libDir.listFiles(windowsKitVersionFilter)) {
            libDirs.put(dir.getName(), dir);
        }
        Set<String> kitVersions = new HashSet<String>();
        kitVersions.addAll(includeDirs.keySet());
        kitVersions.addAll(libDirs.keySet());

        List<String> result = new ArrayList<String>();
        for (String version : kitVersions) {
            File inc = includeDirs.get(version);
            File lib = libDirs.get(version);
            if (inc != null && lib != null) {
                File componentInc = new File(inc, getComponentName());
                File componentLib = new File(lib, getComponentName());
                if (isValidComponentIncludeDir(componentInc) && isValidComponentLibDir(componentLib)) {
                    result.add(version);
                }
            }
        }
        return result.toArray(new String[result.size()]);
    }


    private void putComponent(T component) {
        foundComponents.put(component.getBaseDir(), component);
    }

    protected String getVersionedDisplayName(VersionNumber version, DiscoveryType discoveryType) {
        switch (discoveryType) {
            case USER:
                return USER_PROVIDED + " " + getDisplayName() + " " + version.getMajor();
            case REGISTRY:
                return getDisplayName() + " " + version.getMajor();
            default:
                throw new IllegalArgumentException("Unknown discovery method for " + getDisplayName() + ": " + discoveryType);
        }
    }

    abstract String getComponentName();

    abstract String getDisplayName();

    abstract boolean isValidComponentBaseDir(File baseDir);

    abstract boolean isValidComponentIncludeDir(File includeDir);

    abstract boolean isValidComponentLibDir(File libDir);

    abstract T newComponent(File baseDir, VersionNumber version, DiscoveryType discoveryType);

    private class DescendingComponentVersionComparator implements Comparator<T> {
        @Override
        public int compare(T o1, T o2) {
            return o2.getVersion().compareTo(o1.getVersion());
        }
    }
}
