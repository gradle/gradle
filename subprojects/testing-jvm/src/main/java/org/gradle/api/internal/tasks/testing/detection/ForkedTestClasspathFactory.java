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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import org.gradle.api.internal.classpath.ModuleRegistry;
import org.gradle.api.internal.tasks.testing.JvmTestExecutionSpec;
import org.gradle.api.internal.tasks.testing.TestFramework;
import org.gradle.api.internal.tasks.testing.TestFrameworkDistributionModule;
import org.gradle.api.internal.tasks.testing.worker.ForkedTestClasspath;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.util.internal.CollectionUtils;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Iterator;
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
    private final ClassDetectorFactory classDetectorFactory;

    public ForkedTestClasspathFactory(ModuleRegistry moduleRegistry) {
        this(moduleRegistry, ClassLoadingClassDetector::new);
    }

    @VisibleForTesting
    public ForkedTestClasspathFactory(ModuleRegistry moduleRegistry, ClassDetectorFactory classDetectorFactory) {
        this.moduleRegistry = moduleRegistry;
        this.classDetectorFactory = classDetectorFactory;
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

        AdditionalClasspath unfiltered = new AdditionalClasspath(
            testFramework.getWorkerApplicationClasspathModules(),
            testFramework.getWorkerApplicationModulepathModules(),
            testFramework.getWorkerImplementationClasspathModules(),
            testFramework.getWorkerImplementationModulepathModules()
        );

        AdditionalClasspath filtered = filterAdditionalClasspath(classpath, modulepath, unfiltered);

        // The test's runtimeClasspath already includes the test framework's implementation modules.
        // No need to load anything extra ourselves.
        if (filtered.isEmpty()) {
            return new ForkedTestClasspath(
                ImmutableList.copyOf(classpath), ImmutableList.copyOf(modulepath),
                withImplementation(ImmutableList.of()), ImmutableList.of()
            );
        }

        // There are some framework implementation dependencies which are not present on the testRuntimeClasspath.
        // It is not sufficient to just load the missing modules, since some classes which are referenced
        // by the loaded modules may exist in the versions of the present dependencies from the test classpath.
        // For example, junit-platform-launcher 1.7+ depends on ClassNamePatternFilterUtils from junit-platform-commons 1.7+.
        // If the test classpath contains junit-platform-commons 1.6, then the test process will fail to load the required
        // class.
        //
        // So, even if some framework dependencies are already present on the test classpath, we still need to load
        // the entire set of framework implementation dependencies from the distribution in order to ensure that all implementation
        // dependencies on the classpath share a version. This can _still_ lead to duplicates on the classpath, but it is at least
        // avoidable if the user adds junit-platform-launcher to their test runtime classpath, which they should be doing, since
        // distribution loading is deprecated in Gradle 8.2.
        return getClasspathWithAdditionalModules(classpath, modulepath, unfiltered, isModule);
    }

    /**
     * Creates a classpath for the forked process which injects the additional modules from
     * {@code additional} into the classpath provided by {@code classpath} and {@code modulepath}.
     */
    private ForkedTestClasspath getClasspathWithAdditionalModules(
        Iterable<? extends File> classpath,
        Iterable<? extends File> modulepath,
        AdditionalClasspath additional,
        boolean isModule
    ) {
        // TODO #13955: Enable this deprecation in 8.2
        // We don't have enough time in 8.1 to write the documentation and update our own tests.
//        DeprecationLogger.deprecateIndirectUsage("The automatic loading of test framework implementation dependencies")
//            .withAdvice("Declare the desired test framework directly on the test suite or explicitly declare the test framework implementation dependencies on the test's runtime classpath.")
//            .willBeRemovedInGradle9()
//            .withUpgradeGuideSection(8, "test_framework_implementation_dependencies")
//            .nagUser();

        if (isModule) {
            return new ForkedTestClasspath(
                pathWithAdditionalModules(classpath, additional.applicationClasspath),
                pathWithAdditionalModules(modulepath, additional.applicationModulepath),
                withImplementation(loadDistributionUrls(additional.implementationClasspath)),
                loadDistributionUrls(additional.implementationModulepath)
            );
        } else {
            // For non-module tests, add all additional distribution modules to the classpath.
            List<TestFrameworkDistributionModule> additionalApplicationClasspath = ImmutableList.<TestFrameworkDistributionModule>builder()
                .addAll(additional.applicationClasspath)
                .addAll(additional.applicationModulepath)
                .build();
            List<TestFrameworkDistributionModule> additionalImplementationClasspath = ImmutableList.<TestFrameworkDistributionModule>builder()
                .addAll(additional.implementationClasspath)
                .addAll(additional.implementationModulepath)
                .build();

            return new ForkedTestClasspath(
                pathWithAdditionalModules(classpath, additionalApplicationClasspath),
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
    private ImmutableList<File> pathWithAdditionalModules(Iterable<? extends File> testFiles, List<TestFrameworkDistributionModule> additionalModules) {
        return ImmutableList.<File>builder()
            .addAll(testFiles)
            .addAll(loadDistributionFiles(additionalModules))
            .build();
    }

    private ImmutableList<File> loadDistributionFiles(List<TestFrameworkDistributionModule> moduleNames) {
        return loadFromDistribution(moduleNames, ClassPath::getAsFiles);
    }

    private ImmutableList<URL> loadDistributionUrls(List<TestFrameworkDistributionModule> moduleNames) {
        return loadFromDistribution(moduleNames, ClassPath::getAsURLs);
    }

    private <T> ImmutableList<T> loadFromDistribution(List<TestFrameworkDistributionModule> moduleNames, Function<ClassPath, List<T>> extractor) {
        ImmutableList.Builder<T> outputFiles = ImmutableList.builder();

        if (LOGGER.isDebugEnabled() && !moduleNames.isEmpty()) {
            LOGGER.debug("Loaded additional modules from the Gradle distribution: " + Joiner.on(",").join(moduleNames));
        }

        for (TestFrameworkDistributionModule module : moduleNames) {
            ClassPath cp = moduleRegistry.getExternalModule(module.getModuleName()).getImplementationClasspath();
            outputFiles.addAll(extractor.apply(cp));
        }

        return outputFiles.build();
    }

    private static class AdditionalClasspath {

        public final List<TestFrameworkDistributionModule> applicationClasspath;
        public final List<TestFrameworkDistributionModule> applicationModulepath;
        public final List<TestFrameworkDistributionModule> implementationClasspath;
        public final List<TestFrameworkDistributionModule> implementationModulepath;

        public AdditionalClasspath(
            List<TestFrameworkDistributionModule> applicationClasspath,
            List<TestFrameworkDistributionModule> applicationModulepath,
            List<TestFrameworkDistributionModule> implementationClasspath,
            List<TestFrameworkDistributionModule> implementationModulepath
        ) {
            this.applicationClasspath = applicationClasspath;
            this.applicationModulepath = applicationModulepath;
            this.implementationClasspath = implementationClasspath;
            this.implementationModulepath = implementationModulepath;
        }

        public boolean isEmpty() {
            return applicationClasspath.isEmpty() && applicationModulepath.isEmpty() &&
                implementationClasspath.isEmpty() && implementationModulepath.isEmpty();
        }
    }

    /**
     * Filters the provided {@code unfiltered} additional classpath to only contain modules which are not already
     * present in {@code classpath} and {@code modulepath}. This operates in a two-step process. First, it attempts
     * to parse the names the jars in the provided classpath to quickly determine if the additional modules already
     * exist. This is brittle and prone to errors, but much faster than the second step. If this step fails to filter
     * all additional modules, the second step creates a {@link ClassLoader} based on the provided classpath and
     * modulepath and attempts to discover the modules which are already present.
     */
    private AdditionalClasspath filterAdditionalClasspath(Iterable<? extends File> classpath, Iterable<? extends File> modulepath, AdditionalClasspath unfiltered) {
        AdditionalClasspath fastFiltered = filterFast(classpath, modulepath, unfiltered);

        if (fastFiltered.isEmpty()) {
            return fastFiltered;
        }

        return filterSlow(classpath, modulepath, fastFiltered);
    }

    /**
     * Filters additional modules based on jar file names.
     */
    private AdditionalClasspath filterFast(Iterable<? extends File> classpath, Iterable<? extends File> modulepath, AdditionalClasspath unfiltered) {
        AdditionalClasspath mutable = new AdditionalClasspath(
            new ArrayList<>(unfiltered.applicationClasspath),
            new ArrayList<>(unfiltered.applicationModulepath),
            new ArrayList<>(unfiltered.implementationClasspath),
            new ArrayList<>(unfiltered.implementationModulepath)
        );

        // Filter additional modules which are provided by the classpath
        Iterator<? extends File> it = classpath.iterator();
        while (it.hasNext() && !mutable.isEmpty()) {
            String name = it.next().getName();
            mutable.applicationClasspath.removeIf(module -> module.getJarFilePattern().matcher(name).matches());
            mutable.applicationModulepath.removeIf(module -> module.getJarFilePattern().matcher(name).matches());
            mutable.implementationClasspath.removeIf(module -> module.getJarFilePattern().matcher(name).matches());
            mutable.implementationModulepath.removeIf(module -> module.getJarFilePattern().matcher(name).matches());
        }

        // Filter additional modules which are provided by the modulepath
        it = modulepath.iterator();
        while (it.hasNext() && !mutable.isEmpty()) {
            String name = it.next().getName();
            mutable.applicationClasspath.removeIf(module -> module.getJarFilePattern().matcher(name).matches());
            mutable.applicationModulepath.removeIf(module -> module.getJarFilePattern().matcher(name).matches());
            mutable.implementationClasspath.removeIf(module -> module.getJarFilePattern().matcher(name).matches());
            mutable.implementationModulepath.removeIf(module -> module.getJarFilePattern().matcher(name).matches());
        }

        return mutable;
    }

    /**
     * Filters additional modules by constructing a {@link ClassLoader} and attempting to load classes from the additional modules.
     */
    private AdditionalClasspath filterSlow(Iterable<? extends File> classpath, Iterable<? extends File> modulepath, AdditionalClasspath unfiltered) {
        try (ClassDetector classDetector = classDetectorFactory.create(classpath, modulepath)) {
            return new AdditionalClasspath(
                classDetector.withoutDetectedModules(unfiltered.applicationClasspath),
                classDetector.withoutDetectedModules(unfiltered.applicationModulepath),
                classDetector.withoutDetectedModules(unfiltered.implementationClasspath),
                classDetector.withoutDetectedModules(unfiltered.implementationModulepath)
            );
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    public interface ClassDetector extends Closeable {
        boolean hasClass(String className);

        default List<TestFrameworkDistributionModule> withoutDetectedModules(List<TestFrameworkDistributionModule> modules) {
            ImmutableList.Builder<TestFrameworkDistributionModule> builder = ImmutableList.builder();
            for (TestFrameworkDistributionModule module : modules) {
                if (!hasClass(module.getExampleClassName())) {
                    builder.add(module);
                }
            }
            return builder.build();
        }
    }

    public interface ClassDetectorFactory {
        ClassDetector create(Iterable<? extends File> classpath, Iterable<? extends File> modulepath);
    }

    public static class ClassLoadingClassDetector implements ClassDetector {
        private final URLClassLoader classLoader;

        public ClassLoadingClassDetector(Iterable<? extends File> classpath, Iterable<? extends File> modulepath) {
            ClassPath cp = DefaultClassPath.of(Iterables.concat(classpath, modulepath));
            classLoader = new URLClassLoader(cp.getAsURLArray());
        }

        @Override
        public boolean hasClass(String className) {
            // Load the class resource instead of calling `loadClass` in order to
            // avoid parsing the entire class file and any referenced classes.
            String path = className.replace('.', '/').concat(".class");
            return classLoader.findResource(path) != null;
        }

        @Override
        public void close() throws IOException {
            classLoader.close();
        }
    }
}
