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
import org.gradle.testing.testengine.util.Inputs;
import org.junit.platform.commons.logging.Logger;
import org.junit.platform.commons.logging.LoggerFactory;
import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.engine.discovery.ClassSelector;
import org.junit.platform.engine.discovery.DirectorySelector;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.engine.discovery.FileSelector;
import org.junit.platform.engine.support.discovery.SelectorResolver;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class ResourceBasedSelectorResolver implements SelectorResolver {
    public static final Logger LOGGER = LoggerFactory.getLogger(ResourceBasedSelectorResolver.class);

    private final DirectoryScanner directoryScanner = new DirectoryScanner();

    @Override
    public Resolution resolve(ClassSelector selector, Context context) {
        if (ResourceBasedTestEngine.ENGINE_DUMMY_CLASS_NAME.equals(selector.getClassName())) {
            return Resolution.selectors(Collections.singleton(DiscoverySelectors.selectDirectory(Inputs.getTestResourcesRootDir())));
        } else {
            return Resolution.unresolved();
        }
    }

    @Override
    public Resolution resolve(DirectorySelector selector, Context context) {
        List<File> contents = new ArrayList<>(directoryScanner.scanDirectory(selector.getDirectory(), true));

        if (!contents.isEmpty()) {
            Set<DiscoverySelector> selectors = new LinkedHashSet<>();
            contents.stream()
                .filter(File::isFile)
                .forEach(file -> selectors.add(DiscoverySelectors.selectFile(file.getAbsolutePath())));
            contents.stream()
                .filter(File::isDirectory)
                .forEach(file -> selectors.add(DiscoverySelectors.selectDirectory(file.getAbsolutePath())));

            return Resolution.selectors(selectors);
        } else {
            return Resolution.unresolved();
        }
    }

    @Override
    public Resolution resolve(FileSelector selector, Context context) {
        File file = selector.getFile();
        if (directoryScanner.getTestFileParser().isValidTestDefinitionFile(file)) {
            LOGGER.info(() -> "Test specification file: " + file.getAbsolutePath());

            Set<Match> tests = directoryScanner.getTestFileParser().parseTestNames(file).stream()
                    .map(testName -> context.addToParent(parent -> Optional.of(new ResourceBasedTestDescriptor(parent.getUniqueId(), file, testName))))
                    .map(Optional::get)
                    .map(Match::exact)
                    .collect(Collectors.toSet());
            return Resolution.matches(tests);
        } else {
            return Resolution.unresolved();
        }
    }
}
