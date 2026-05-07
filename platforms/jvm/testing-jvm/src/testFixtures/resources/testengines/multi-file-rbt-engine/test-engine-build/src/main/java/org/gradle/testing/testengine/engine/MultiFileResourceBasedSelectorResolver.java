/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.testing.testengine.engine;

import org.gradle.testing.testengine.descriptor.ResourceBasedTestDescriptor;
import org.gradle.testing.testengine.util.DirectoryScanner;
import org.junit.platform.commons.logging.Logger;
import org.junit.platform.commons.logging.LoggerFactory;
import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.engine.discovery.DirectorySelector;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.engine.discovery.FileSelector;
import org.junit.platform.engine.support.discovery.SelectorResolver;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Collectors;

public class MultiFileResourceBasedSelectorResolver implements SelectorResolver {
    public static final Logger LOGGER = LoggerFactory.getLogger(MultiFileResourceBasedSelectorResolver.class);

    private final DirectoryScanner directoryScanner = new DirectoryScanner();

    @Override
    public Resolution resolve(DirectorySelector selector, Context context) {
        List<File> contents = new ArrayList<>(directoryScanner.scanDirectory(selector.getDirectory(), true));

        if (!contents.isEmpty()) {
            Set<File> testDirs = contents.stream()
                .filter(File::isDirectory)
                .filter(this::hasTestHalves)
                .collect(Collectors.toSet());

            Set<Match> tests = new HashSet<>();
            testDirs.stream()
                .map(dir -> context.addToParent(parent -> Optional.of(new ResourceBasedTestDescriptor(parent.getUniqueId(), new File(dir, "first-half.txt"), dir.getName()))))
                .map(Optional::get)
                .map(Match::exact)
                .forEach(tests::add);
            testDirs.stream()
                .map(dir -> context.addToParent(parent -> Optional.of(new ResourceBasedTestDescriptor(parent.getUniqueId(), new File(dir, "second-half.txt"), dir.getName()))))
                .map(Optional::get)
                .map(Match::exact)
                .forEach(tests::add);

            if (!tests.isEmpty()) {
                return Resolution.matches(tests);
            }
        }
        return Resolution.unresolved();
    }

    private boolean hasTestHalves(File dir) {
        File[] files = dir.listFiles();
        if (files == null) {
            return false;
        }
        Set<String> fileNames = new LinkedHashSet<>();
        for (File file : files) {
            fileNames.add(file.getName());
        }
        return fileNames.contains("first-half.txt") && fileNames.contains("second-half.txt");
    }
}
