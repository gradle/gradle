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

package org.gradle.api.internal.tasks.testing.detection;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import org.gradle.api.file.EmptyFileVisitor;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.ReproducibleFileVisitor;
import org.gradle.api.internal.file.RelativeFile;
import org.gradle.api.internal.tasks.testing.ClassTestDefinition;
import org.gradle.api.internal.tasks.testing.DirectoryBasedTestDefinition;
import org.gradle.api.internal.tasks.testing.JvmTestExecutionSpec;
import org.gradle.api.internal.tasks.testing.TestDefinition;
import org.gradle.api.internal.tasks.testing.TestDefinitionProcessor;
import org.gradle.api.internal.tasks.testing.detection.distribution.ByTopLevelContainerTestDistributor;
import org.gradle.api.internal.tasks.testing.detection.distribution.ByIndividualTestTestDistributor;
import org.gradle.api.internal.tasks.testing.detection.distribution.TestDistributor;
import org.gradle.api.tasks.testing.distribution.TestDistributionStrategy;
import org.jspecify.annotations.NonNull;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.platform.launcher.EngineFilter.excludeEngines;
import static org.junit.platform.launcher.EngineFilter.includeEngines;
import static org.junit.platform.launcher.TagFilter.excludeTags;
import static org.junit.platform.launcher.TagFilter.includeTags;

/**
 * The default test definition scanner.
 * <p>
 * Depending on the availability of a test framework detector,
 * a detection or filename scan is performed to find test definitions.
 * <p>
 * Test definitions include both class-based and non-class-based tests.
 */
public class DefaultTestDetector implements TestDetector {
    private static final Pattern ANONYMOUS_CLASS_NAME = Pattern.compile(".*\\$\\d+");
    private final JvmTestExecutionSpec spec;
    private final ImmutableList<File> testClasspath;
    private final TestFrameworkDetector testFrameworkDetector;
    private final TestDefinitionProcessor<TestDefinition> testDefinitionProcessor;
    private final List<String> discoveryIncludeEngines;
    private final List<String> discoveryExcludeEngines;
    private final List<String> discoveryIncludeTags;
    private final List<String> discoveryExcludeTags;

    public DefaultTestDetector(JvmTestExecutionSpec spec,
                               ImmutableList<File> testClasspath,
                               TestFrameworkDetector testFrameworkDetector,
                               TestDefinitionProcessor<TestDefinition> testDefinitionProcessor,
                               List<String> discoveryIncludeEngines,
                               List<String> discoveryExcludeEngines,
                               List<String> discoveryIncludeTags,
                               List<String> discoveryExcludeTags
    ) {
        this.spec = spec;
        this.testClasspath = testClasspath;
        this.testFrameworkDetector = testFrameworkDetector;
        this.testDefinitionProcessor = testDefinitionProcessor;
        this.discoveryIncludeEngines = discoveryIncludeEngines;
        this.discoveryExcludeEngines = discoveryExcludeEngines;
        this.discoveryIncludeTags = discoveryIncludeTags;
        this.discoveryExcludeTags = discoveryExcludeTags;
    }

    @Override
    public void detect() {
        if (spec.isUseDaemonSideTestDiscovery()) {
            if (testFrameworkDetector != null) {
                throw new UnsupportedTestDiscoveryException(spec.getPath());
            } else {
                discoveryScan();
            }
        } else {
            if (testFrameworkDetector == null) {
                filenameScan();
            } else {
                detectionScan();
            }
        }
    }

    private void discoveryScan() {
        TestPlan testPlan = discoverInIsolatedClassLoader();
        TestDistributor distributor = createDistributor(spec.getTestDistributionStrategy());
        distributor.distribute(testPlan, testDefinitionProcessor);
    }

    private TestDistributor createDistributor(TestDistributionStrategy strategy) {
        return switch (strategy) {
            case BY_TOP_LEVEL_TEST_CONTAINER -> new ByTopLevelContainerTestDistributor();
            case BY_INDIVIDUAL_TEST -> new ByIndividualTestTestDistributor();
        };
    }

    private TestPlan discoverInIsolatedClassLoader() {
        try (URLClassLoader classLoader = createTestDiscoveryClassLoader()) {
            ClassLoader original = Thread.currentThread().getContextClassLoader();
            try {
                Thread.currentThread().setContextClassLoader(classLoader);
                return scanForTestPlan();
            } finally {
                Thread.currentThread().setContextClassLoader(original);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private URLClassLoader createTestDiscoveryClassLoader() {
        Set<File> testClassesDirs = spec.getTestClassesDirs().getFiles();
        ClassLoader parent = Thread.currentThread().getContextClassLoader();
        URL[] urls = Streams.concat(testClasspath.stream(), testClassesDirs.stream())
            .map(file -> {
                try {
                    return file.toURI().toURL();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }).toArray(URL[]::new);
        return new URLClassLoader(urls, parent);
    }

    private TestPlan scanForTestPlan() {
        LauncherDiscoveryRequestBuilder requestBuilder = LauncherDiscoveryRequestBuilder.request();
        Set<Path> classpathRoots = spec.getTestClassesDirs().getFiles().stream()
            .map(File::toPath)
            .collect(Collectors.toSet());
        requestBuilder.selectors(DiscoverySelectors.selectClasspathRoots(classpathRoots));

        for (File dir : spec.getCandidateTestDefinitionDirs()) {
            requestBuilder.selectors(DiscoverySelectors.selectDirectory(dir));
        }

        if (!discoveryIncludeEngines.isEmpty()) {
            requestBuilder.filters(includeEngines(discoveryIncludeEngines));
        }
        if (!discoveryExcludeEngines.isEmpty()) {
            requestBuilder.filters(excludeEngines(discoveryExcludeEngines));
        }
        if (!discoveryIncludeTags.isEmpty()) {
            requestBuilder.filters(includeTags(discoveryIncludeTags));
        }
        if (!discoveryExcludeTags.isEmpty()) {
            requestBuilder.filters(excludeTags(discoveryExcludeTags));
        }

        Launcher launcher = LauncherFactory.create();
        return launcher.discover(requestBuilder.build());
    }

    private void detectionScan() {
        testFrameworkDetector.startDetection(testDefinitionProcessor);
        spec.getCandidateClassFiles().visit(new ClassFileVisitor() {
            @Override
            public void visitClassFile(FileVisitDetails fileDetails) {
                testFrameworkDetector.processTestClass(new RelativeFile(fileDetails.getFile(), fileDetails.getRelativePath()));
            }
        });
    }

    private void filenameScan() {
        spec.getCandidateClassFiles().visit(new ClassFileVisitor() {
            @Override
            public void visitClassFile(FileVisitDetails fileDetails) {
                TestDefinition testDefinition = new ClassTestDefinition(getClassName(fileDetails));
                testDefinitionProcessor.processTestDefinition(testDefinition);
            }
        });
        spec.getCandidateTestDefinitionDirs().forEach(dir -> {
            TestDefinition testDefinition = new DirectoryBasedTestDefinition(dir);
            testDefinitionProcessor.processTestDefinition(testDefinition);
        });
    }

    private abstract class ClassFileVisitor extends EmptyFileVisitor implements ReproducibleFileVisitor {
        @Override
        public void visitFile(@NonNull FileVisitDetails fileDetails) {
            if (isClass(fileDetails) && !isAnonymousClass(fileDetails)) {
                visitClassFile(fileDetails);
            }
        }

        abstract void visitClassFile(FileVisitDetails fileDetails);

        private boolean isAnonymousClass(FileVisitDetails fileVisitDetails) {
            return ANONYMOUS_CLASS_NAME.matcher(getClassName(fileVisitDetails)).matches();
        }

        private boolean isClass(FileVisitDetails fileVisitDetails) {
            String fileName = fileVisitDetails.getFile().getName();
            return fileName.endsWith(".class") && !"module-info.class".equals(fileName);
        }

        @Override
        public boolean isReproducibleFileOrder() {
            return true;
        }
    }

    private String getClassName(FileVisitDetails fileDetails) {
        return fileDetails.getRelativePath().getPathString().replaceAll("\\.class", "").replace('/', '.');
    }
}
