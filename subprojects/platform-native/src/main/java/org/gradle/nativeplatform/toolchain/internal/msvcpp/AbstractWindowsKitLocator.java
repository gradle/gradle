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

import net.rubygrapefruit.platform.MissingRegistryEntryException;
import net.rubygrapefruit.platform.WindowsRegistry;
import org.gradle.internal.FileUtils;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.util.TreeVisitor;
import org.gradle.util.VersionNumber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

public abstract class AbstractWindowsKitLocator<T extends WindowsKitComponent> {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractWindowsKitLocator.class);
    private final Map<File, Set<T>> foundComponents = new HashMap<File, Set<T>>();
    protected final OperatingSystem os;
    private final WindowsRegistry windowsRegistry;
    private boolean initialised;

    protected enum DiscoveryType { AUTO, USER }
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

    AbstractWindowsKitLocator(OperatingSystem os, WindowsRegistry windowsRegistry) {
        this.os = os;
        this.windowsRegistry = windowsRegistry;
    }

    private WindowsKitComponentLocator.SearchResult<T> locateUserSpecifiedComponent(File candidate) {
        File windowsKitDir = FileUtils.canonicalize(candidate);
        String[] versionDirs = getComponentVersionDirs(windowsKitDir);
        if (versionDirs.length > 0) {
            for (String versionDir : versionDirs) {
                VersionNumber version = VersionNumber.withPatchNumber().parse(versionDir);
                LOGGER.debug("Found {} {} ({}) at {}", getComponentName(), version.toString(), versionDir, windowsKitDir);
                if (!foundComponents.containsKey(windowsKitDir)) {
                    putComponent(newComponent(windowsKitDir, version, DiscoveryType.USER));
                }
            }
            return new ComponentFound(getBestComponent(candidate));
        } else {
            return new ComponentNotFound(String.format("The specified installation directory '%s' does not appear to contain a %s installation.", candidate, getComponentName()));
        }
    }

    private WindowsKitComponentLocator.SearchResult<T> locateDefaultComponent() {
        T selected = getBestComponent();
        return selected == null
            ? new ComponentNotFound("Could not locate a " + getComponentName() + " installation using the Windows registry.")
            : new ComponentFound(selected);
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
            File lib = includeDirs.get(version);
            if (inc != null && lib != null) {
                File componentInc = new File(inc, getComponentName());
                File componentLib = new File(lib, getComponentName());
                if (componentInc.isDirectory() && componentLib.isDirectory()) {
                    result.add(version);
                }
            }
        }
        return result.toArray(new String[result.size()]);
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
        for (Set<T> components : foundComponents.values()) {
            candidates.addAll(components);
        }
        return candidates.isEmpty() ? null : candidates.iterator().next();
    }

    private void locateComponentsInRegistry() {
        for (String baseKey : REGISTRY_BASEPATHS) {
            locateComponentsInRegistry(baseKey);
        }
    }

    private void locateComponentsInRegistry(String baseKey) {
        String[] keys = {
                REGISTRY_KIT_10
        };

        for (String key : keys) {
            try {
                File windowsKitDir = FileUtils.canonicalize(new File(windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, baseKey + REGISTRY_ROOTPATH_KIT, key)));
                String[] versionDirs = getComponentVersionDirs(windowsKitDir);
                if (versionDirs.length > 0) {
                    for (String versionDir : versionDirs) {
                        VersionNumber version = VersionNumber.withPatchNumber().parse(versionDir);
                        LOGGER.debug("Found {} {} at {}", getComponentName(), version.toString(), windowsKitDir);
                        putComponent(newComponent(windowsKitDir, version, DiscoveryType.AUTO));
                    }
                } else {
                    LOGGER.debug("Ignoring candidate {} directory {} as it does not look like a {} installation.", getComponentName(), windowsKitDir, getComponentName());
                }
            } catch (MissingRegistryEntryException e) {
                // Ignore the version if the string cannot be read
            }
        }
    }

    private void putComponent(T component) {
        Set<T> components = foundComponents.get(component.getBaseDir());
        if (components == null) {
            components = new HashSet<T>();
            foundComponents.put(component.getBaseDir(), components);
        }
        components.add(component);
    }

    public WindowsKitComponentLocator.SearchResult<T> locateComponents(File candidate) {
        if (!initialised) {
            locateComponentsInRegistry();
            initialised = true;
        }

        if (candidate != null) {
            return locateUserSpecifiedComponent(candidate);
        }

        return locateDefaultComponent();
    }

    abstract String getComponentName();

    abstract T newComponent(File baseDir, VersionNumber version, DiscoveryType discoveryType);

    private class ComponentFound implements WindowsKitComponentLocator.SearchResult<T> {
        private final T component;

        public ComponentFound(T component) {
            this.component = component;
        }

        public T getComponent() {
            return component;
        }

        public boolean isAvailable() {
            return true;
        }

        public void explain(TreeVisitor<? super String> visitor) {
        }
    }

    private class ComponentNotFound implements WindowsKitComponentLocator.SearchResult<T> {
        private final String message;

        private ComponentNotFound(String message) {
            this.message = message;
        }

        public T getComponent() {
            return null;
        }

        public boolean isAvailable() {
            return false;
        }

        public void explain(TreeVisitor<? super String> visitor) {
            visitor.node(message);
        }
    }

    private class DescendingComponentVersionComparator implements Comparator<T> {
        @Override
        public int compare(T o1, T o2) {
            return o2.getVersion().compareTo(o1.getVersion());
        }
    }
}
