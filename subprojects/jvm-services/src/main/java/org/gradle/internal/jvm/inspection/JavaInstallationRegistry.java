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

package org.gradle.internal.jvm.inspection;

import com.google.common.annotations.VisibleForTesting;
import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.logging.progress.ProgressLogger;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.CallableBuildOperation;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.jvm.toolchain.internal.InstallationLocation;
import org.gradle.jvm.toolchain.internal.InstallationSupplier;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@ServiceScope(Scopes.Build.class)
public class JavaInstallationRegistry {
    private final BuildOperationExecutor executor;
    private final Installations installations;
    private final JvmMetadataDetector metadataDetector;
    private final Logger logger;
    private final OperatingSystem os;

    private final ProgressLoggerFactory progressLoggerFactory;

    @Inject
    public JavaInstallationRegistry(
        List<InstallationSupplier> suppliers,
        JvmMetadataDetector metadataDetector,
        @Nullable BuildOperationExecutor executor,
        OperatingSystem os,
        ProgressLoggerFactory progressLoggerFactory
    ) {
        this(suppliers, metadataDetector, Logging.getLogger(JavaInstallationRegistry.class), executor, os, progressLoggerFactory);
    }

    private JavaInstallationRegistry(
        List<InstallationSupplier> suppliers,
        JvmMetadataDetector metadataDetector,
        Logger logger,
       @Nullable BuildOperationExecutor executor,
        OperatingSystem os,
        ProgressLoggerFactory progressLoggerFactory
    ) {
        this.logger = logger;
        this.executor = executor;
        this.metadataDetector = metadataDetector;
        this.installations = new Installations(() -> maybeCollectInBuildOperation(suppliers));
        this.os = os;
        this.progressLoggerFactory = progressLoggerFactory;
    }

    @VisibleForTesting
    static JavaInstallationRegistry withLogger(
        List<InstallationSupplier> suppliers,
        JvmMetadataDetector metadataDetector,
        Logger logger,
        BuildOperationExecutor executor,
        ProgressLoggerFactory progressLoggerFactory
    ) {
        return new JavaInstallationRegistry(suppliers, metadataDetector, logger, executor, OperatingSystem.current(), progressLoggerFactory);
    }

    private Set<InstallationLocation> maybeCollectInBuildOperation(List<InstallationSupplier> suppliers) {
        if (executor != null) {
            return executor.call(new ToolchainDetectionBuildOperation(() -> collectInstallations(suppliers)));
        } else {
            return collectInstallations(suppliers);
        }
    }

    protected Set<InstallationLocation> listInstallations() {
        return installations.get();
    }

    public List<JvmToolchainMetadata> toolchains() {
        ProgressLogger progressLogger = progressLoggerFactory.newOperation(JavaInstallationRegistry.class).start("Discovering toolchains", "Discovering toolchains");
        List<JvmToolchainMetadata> result = listInstallations()
            .parallelStream()
            .peek(location -> progressLogger.progress("Extracting toolchain metadata from " + location.getDisplayName()))
            .map(this::resolveMetadata)
            .collect(Collectors.toList());
        progressLogger.completed();
        return result;
    }

    private JvmToolchainMetadata resolveMetadata(InstallationLocation location) {
        JvmInstallationMetadata metadata = metadataDetector.getMetadata(location);
        return new JvmToolchainMetadata(metadata, location);
    }

    public void addInstallation(InstallationLocation installation) {
        installations.add(installation);
    }

    private Set<InstallationLocation> collectInstallations(List<InstallationSupplier> suppliers) {
        return suppliers.parallelStream()
            .peek(x -> logger.debug("Discovering toolchains provided via {}", x.getSourceName()))
            .map(InstallationSupplier::get)
            .flatMap(Set::stream)
            .filter(this::installationExists)
            .map(this::canonicalize)
            .map(this::maybeGetEnclosedInstallation)
            .filter(this::installationHasExecutable)
            .filter(distinctByKey(InstallationLocation::getLocation))
            .collect(Collectors.toSet());
    }

    protected boolean installationExists(InstallationLocation installationLocation) {
        File file = installationLocation.getLocation();
        if (!file.exists()) {
            logger.warn("Directory {} used for java installations does not exist", installationLocation.getDisplayName());
            return false;
        }
        if (!file.isDirectory()) {
            logger.warn("Path for java installation {} points to a file, not a directory", installationLocation.getDisplayName());
            return false;
        }
        return true;
    }

    protected boolean installationHasExecutable(InstallationLocation installationLocation) {
        if (!hasJavaExecutable(installationLocation.getLocation())) {
            logger.warn("Path for java installation {} does not contain a java executable", installationLocation.getDisplayName());
            return false;
        }
        return true;
    }

    private InstallationLocation canonicalize(InstallationLocation location) {
        final File file = location.getLocation();
        try {
            final File canonicalFile = file.getCanonicalFile();
            final File javaHome = findJavaHome(canonicalFile);
            return new InstallationLocation(javaHome, location.getSource(), location.isAutoProvisioned());
        } catch (IOException e) {
            throw new GradleException(String.format("Could not canonicalize path to java installation: %s.", file), e);
        }
    }

    private InstallationLocation maybeGetEnclosedInstallation(InstallationLocation location) {
        final File home = location.getLocation();
        final File parentPath = home.getParentFile();
        final boolean isEmbeddedJre = home.getName().equalsIgnoreCase("jre");
        if (isEmbeddedJre && hasJavaExecutable(parentPath)) {
            return new InstallationLocation(parentPath, location.getSource());
        }
        return location;
    }

    private File findJavaHome(File potentialHome) {
        if (os.isMacOsX() && new File(potentialHome, "Contents/Home").exists()) {
            return new File(potentialHome, "Contents/Home");
        }
        final File standaloneJre = new File(potentialHome, "jre");
        if (!hasJavaExecutable(potentialHome) && hasJavaExecutable(standaloneJre)) {
            return standaloneJre;
        }
        return potentialHome;
    }

    private boolean hasJavaExecutable(File potentialHome) {
        return new File(potentialHome, os.getExecutableName("bin/java")).exists();
    }

    public static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
        Set<Object> seen = ConcurrentHashMap.newKeySet();
        return t -> seen.add(keyExtractor.apply(t));
    }


    private static class ToolchainDetectionBuildOperation implements CallableBuildOperation<Set<InstallationLocation>> {
        private final Callable<Set<InstallationLocation>> detectionStrategy;

        public ToolchainDetectionBuildOperation(Callable<Set<InstallationLocation>> detectionStrategy) {
            this.detectionStrategy = detectionStrategy;
        }

        @Override
        public Set<InstallationLocation> call(BuildOperationContext context) throws Exception {
            return detectionStrategy.call();
        }

        @Override
        public BuildOperationDescriptor.Builder description() {
            return BuildOperationDescriptor
                .displayName("Toolchain detection")
                .progressDisplayName("Detecting local java toolchains");
        }
    }

    private static class Installations {

        private final Supplier<Set<InstallationLocation>> initializer;

        private Set<InstallationLocation> locations = null;

        Installations(Supplier<Set<InstallationLocation>> initializer) {
            this.initializer = initializer;
        }

        synchronized Set<InstallationLocation> get() {
            initIfNeeded();
            return locations;
        }

        synchronized void add(InstallationLocation location) {
            initIfNeeded();
            locations.add(location);
        }

        private void initIfNeeded() {
            if (locations == null) {
                locations = initializer.get();
            }
        }

    }

}
