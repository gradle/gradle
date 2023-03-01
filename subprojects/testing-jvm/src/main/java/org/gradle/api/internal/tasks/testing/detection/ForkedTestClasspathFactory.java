/*
 * Copyright 2023 the original author or authors.
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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import org.gradle.api.internal.classpath.ModuleRegistry;
import org.gradle.api.internal.tasks.testing.JvmTestExecutionSpec;
import org.gradle.api.internal.tasks.testing.TestFramework;
import org.gradle.api.internal.tasks.testing.worker.ForkedTestClasspath;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.util.internal.CollectionUtils;

import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.function.Function;

/**
 * Constructs the application and implementation classpaths for a test process,
 * while also optionally loading test framework implementation dependencies from
 * the distribution.
 *
 * @see ForkedTestClasspath
 */
public class ForkedTestClasspathFactory {

    private static final Logger LOGGER = Logging.getLogger(ForkedTestClasspathFactory.class);

    private final ModuleRegistry moduleRegistry;

    public ForkedTestClasspathFactory(ModuleRegistry moduleRegistry) {
        this.moduleRegistry = moduleRegistry;
    }

    public ForkedTestClasspath create(
        Iterable<? extends File> classpath,
        Iterable<? extends File> modulepath,
        TestFramework testFramework,
        boolean isModule
    ) {
        if (!testFramework.getUseDistributionDependencies()) {
            return new ForkedTestClasspath(
                ImmutableList.copyOf(classpath), ImmutableList.copyOf(modulepath),
                withImplementation(ImmutableList.of()), ImmutableList.of()
            );
        }

        // TODO: Follow up work is necessary to avoid loading duplicate classes from
        // both the testRuntimeClasspath and the distribution. We can do this
        // cheaply by testing jar file names, though this is ugly. Actually creating
        // a ClassLoader and testing for classes is expensive, but may be necessary.

        // TODO: Deprecate loading framework implementation modules from the distribution.

        if (isModule) {
            return new ForkedTestClasspath(
                pathWithAdditionalJars(classpath, testFramework.getWorkerApplicationClasspathModuleNames()),
                pathWithAdditionalJars(modulepath, testFramework.getWorkerApplicationModulepathModuleNames()),
                withImplementation(loadDistributionUrls(testFramework.getWorkerImplementationClasspathModuleNames())),
                loadDistributionUrls(testFramework.getWorkerImplementationModulepathModuleNames())
            );
        } else {
            // For non-module tests, add all additional distribution jars to the classpath.
            List<String> additionalApplicationClasspath = ImmutableList.<String>builder()
                .addAll(testFramework.getWorkerApplicationClasspathModuleNames())
                .addAll(testFramework.getWorkerApplicationModulepathModuleNames())
                .build();
            List<String> additionalImplementationClasspath = ImmutableList.<String>builder()
                .addAll(testFramework.getWorkerImplementationClasspathModuleNames())
                .addAll(testFramework.getWorkerImplementationModulepathModuleNames())
                .build();

            return new ForkedTestClasspath(
                pathWithAdditionalJars(classpath, additionalApplicationClasspath),
                ImmutableList.copyOf(modulepath),
                withImplementation(loadDistributionUrls(additionalImplementationClasspath)),
                ImmutableList.of()
            );
        }
    }

    /**
     * Constructs the implementation classpath required by the Gradle testing infrastructure
     * while also mixing-in the additional implementation classpath jars required by the
     */
    private ImmutableList<URL> withImplementation(List<URL> additionalImplementationClasspath) {
        return ImmutableList.copyOf(CollectionUtils.flattenCollections(URL.class,
            moduleRegistry.getModule("gradle-core-api").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getModule("gradle-worker-processes").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getModule("gradle-core").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getModule("gradle-logging").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getModule("gradle-logging-api").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getModule("gradle-messaging").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getModule("gradle-files").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getModule("gradle-file-temp").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getModule("gradle-hashing").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getModule("gradle-base-services").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getModule("gradle-enterprise-logging").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getModule("gradle-enterprise-workers").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getModule("gradle-cli").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getModule("gradle-native").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getModule("gradle-testing-base").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getModule("gradle-testing-jvm-infrastructure").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getModule("gradle-testing-junit-platform").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getModule("gradle-process-services").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getModule("gradle-build-operations").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getExternalModule("slf4j-api").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getExternalModule("jul-to-slf4j").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getExternalModule("native-platform").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getExternalModule("kryo").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getExternalModule("commons-lang").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getExternalModule("javax.inject").getImplementationClasspath().getAsURLs(),
            additionalImplementationClasspath
        ));
    }

    /**
     * Create a classpath or modulepath, as a list of files, given both the files provided by the test spec and a list of
     * modules to load from the Gradle distribution.
     *
     * @param testFiles A set of jars, as given from a {@link JvmTestExecutionSpec}'s classpath or modulePath.
     * @param additionalModules The names of any additional modules to load from the Gradle distribution.
     *
     * @return A set of files representing the constructed classpath or modulePath.
     */
    private ImmutableList<File> pathWithAdditionalJars(Iterable<? extends File> testFiles, List<String> additionalModules) {
        return ImmutableList.<File>builder()
            .addAll(testFiles)
            .addAll(loadDistributionFiles(additionalModules))
            .build();
    }

    private ImmutableList<File> loadDistributionFiles(List<String> moduleNames) {
        return loadFromDistribution(moduleNames, ClassPath::getAsFiles);
    }

    private ImmutableList<URL> loadDistributionUrls(List<String> moduleNames) {
        return loadFromDistribution(moduleNames, ClassPath::getAsURLs);
    }

    private <T> ImmutableList<T> loadFromDistribution(List<String> moduleNames, Function<ClassPath, List<T>> extractor) {
        ImmutableList.Builder<T> outputFiles = ImmutableList.builder();

        if (LOGGER.isDebugEnabled() && !moduleNames.isEmpty()) {
            LOGGER.debug("Loaded additional modules from the Gradle distribution: " + Joiner.on(",").join(moduleNames));
        }

        for (String module : moduleNames) {
            ClassPath cp = moduleRegistry.getExternalModule(module).getImplementationClasspath();
            outputFiles.addAll(extractor.apply(cp));
        }

        return outputFiles.build();
    }
}
