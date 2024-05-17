/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.cache.internal;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.RegexFileFilter;
import org.apache.commons.lang.StringUtils;
import org.gradle.cache.CleanupProgressMonitor;
import org.gradle.internal.IoActions;
import org.gradle.internal.cache.MonitoredCleanupAction;
import org.gradle.util.GradleVersion;
import org.gradle.util.internal.DefaultGradleVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.FileFilter;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.apache.commons.io.filefilter.FileFilterUtils.directoryFileFilter;
import static org.gradle.util.internal.CollectionUtils.single;

public class WrapperDistributionCleanupAction implements MonitoredCleanupAction {

    public static final String WRAPPER_DISTRIBUTION_FILE_PATH = "wrapper/dists";
    private static final Logger LOGGER = LoggerFactory.getLogger(WrapperDistributionCleanupAction.class);

    private static final ImmutableMap<String, Pattern> JAR_FILE_PATTERNS_BY_PREFIX;
    private static final String BUILD_RECEIPT_ZIP_ENTRY_PATH = StringUtils.removeStart(DefaultGradleVersion.RESOURCE_NAME, "/");

    static {
        Set<String> prefixes = ImmutableSet.of(
            "gradle-base-services", // 4.x
            "gradle-version-info", // 2.x - 3.x
            "gradle-core" // 1.x
        );
        ImmutableMap.Builder<String, Pattern> builder = ImmutableMap.builder();
        for (String prefix : prefixes) {
            builder.put(prefix, Pattern.compile('^' + Pattern.quote(prefix) + "-\\d.+.jar$"));
        }
        JAR_FILE_PATTERNS_BY_PREFIX = builder.build();
    }

    private final File distsDir;
    private final UsedGradleVersions usedGradleVersions;

    public WrapperDistributionCleanupAction(File gradleUserHomeDirectory, UsedGradleVersions usedGradleVersions) {
        this.distsDir = new File(gradleUserHomeDirectory, WRAPPER_DISTRIBUTION_FILE_PATH);
        this.usedGradleVersions = usedGradleVersions;
    }

    @Nonnull
    @Override
    public String getDisplayName() {
        return "Deleting unused Gradle distributions in " + distsDir;
    }

    @Override
    public boolean execute(@Nonnull CleanupProgressMonitor progressMonitor) {
        long maximumTimestamp = Math.max(0, System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1));
        Set<GradleVersion> usedVersions = this.usedGradleVersions.getUsedGradleVersions();
        Multimap<GradleVersion, File> checksumDirsByVersion = determineChecksumDirsByVersion();
        for (GradleVersion version : checksumDirsByVersion.keySet()) {
            if (!usedVersions.contains(version) && version.compareTo(GradleVersion.current()) < 0) {
                deleteDistributions(version, checksumDirsByVersion.get(version), maximumTimestamp, progressMonitor);
            } else {
                progressMonitor.incrementSkipped(checksumDirsByVersion.get(version).size());
            }
        }
        return true;
    }

    private void deleteDistributions(GradleVersion version, Collection<File> dirs, long maximumTimestamp, CleanupProgressMonitor progressMonitor) {
        Set<File> parentsOfDeletedDistributions = Sets.newLinkedHashSet();
        for (File checksumDir : dirs) {
            if (checksumDir.lastModified() > maximumTimestamp) {
                progressMonitor.incrementSkipped();
                LOGGER.debug("Skipping distribution for {} at {} because it was recently added", version, checksumDir);
            } else {
                /*
                 * An extracted distribution usually looks like:
                 * checksumDir/
                 *      | gradle-5.5.1-bin.zip.ok
                 *      | gradle-5.5.1-bin.zip.lck
                 *      | gradle-5.5.1-bin.zip
                 *      | gradle-5.5.1
                 */
                progressMonitor.incrementDeleted();
                LOGGER.debug("Marking distribution for {} at {} unusable", version, checksumDir);

                // The wrapper uses the .ok file to identify distributions that are safe to use.
                // If we delete anything from the distribution before deleting the OK file, the
                // wrapper will attempt to use the distribution as-is and fail in strange and unrecoverable
                // ways.
                File[] markerFiles = checksumDir.listFiles((dir, name) -> name.endsWith(".ok"));
                boolean canBeDeleted = true;
                if (markerFiles!=null) {
                    for (File markerFile : markerFiles) {
                        canBeDeleted &= markerFile.delete();
                    }
                }
                if (canBeDeleted) {
                    if (FileUtils.deleteQuietly(checksumDir)) {
                        parentsOfDeletedDistributions.add(checksumDir.getParentFile());
                    } else {
                        LOGGER.info("Distribution for {} at {} was not completely deleted.", version, checksumDir);
                    }
                } else {
                    LOGGER.info("Distribution for {} at {} cannot be deleted because Gradle is unable to mark it as unusable.", version, checksumDir);
                }
            }
        }
        for (File parentDir : parentsOfDeletedDistributions) {
            if (listFiles(parentDir).isEmpty()) {
                parentDir.delete();
            }
        }
    }

    private Multimap<GradleVersion, File> determineChecksumDirsByVersion() {
        Multimap<GradleVersion, File> result = ArrayListMultimap.create();
        for (File dir : listDirs(distsDir)) {
            for (File checksumDir : listDirs(dir)) {
                try {
                    GradleVersion gradleVersion = determineGradleVersionFromBuildReceipt(checksumDir);
                    result.put(gradleVersion, checksumDir);
                } catch (Exception e) {
                    LOGGER.debug("Could not determine Gradle version for {}: {} ({})", checksumDir, e.getMessage(), e.getClass().getName());
                }
            }
        }
        return result;
    }

    private GradleVersion determineGradleVersionFromBuildReceipt(File checksumDir) throws Exception {
        List<File> subDirs = listDirs(checksumDir);
        Preconditions.checkArgument(subDirs.size() == 1, "A Gradle distribution must contain exactly one subdirectory: %s", subDirs);
        return determineGradleVersionFromDistribution(single(subDirs));
    }

    @VisibleForTesting
    protected GradleVersion determineGradleVersionFromDistribution(File distributionHomeDir) throws Exception {
        List<File> checkedJarFiles = new ArrayList<File>();
        for (Map.Entry<String, Pattern> entry : JAR_FILE_PATTERNS_BY_PREFIX.entrySet()) {
            List<File> jarFiles = listFiles(new File(distributionHomeDir, "lib"), new RegexFileFilter(entry.getValue()));
            if (!jarFiles.isEmpty()) {
                Preconditions.checkArgument(jarFiles.size() == 1, "A Gradle distribution must contain at most one %s-*.jar: %s", entry.getKey(), jarFiles);
                File jarFile = single(jarFiles);
                GradleVersion gradleVersion = readGradleVersionFromJarFile(jarFile);
                if (gradleVersion != null) {
                    return gradleVersion;
                }
                checkedJarFiles.add(jarFile);
            }
        }
        throw new IllegalArgumentException("No checked JAR file contained a build receipt: " + checkedJarFiles);
    }

    @Nullable
    private GradleVersion readGradleVersionFromJarFile(File jarFile) throws Exception {
        ZipFile zipFile = null;
        try {
            zipFile = new ZipFile(jarFile);
            return readGradleVersionFromBuildReceipt(zipFile);
        } finally {
            IoActions.closeQuietly(zipFile);
        }
    }

    @Nullable
    private GradleVersion readGradleVersionFromBuildReceipt(ZipFile zipFile) throws Exception {
        ZipEntry zipEntry = zipFile.getEntry(BUILD_RECEIPT_ZIP_ENTRY_PATH);
        if (zipEntry == null) {
            return null;
        }
        InputStream in = zipFile.getInputStream(zipEntry);
        try {
            Properties properties = new Properties();
            properties.load(in);
            String versionString = properties.getProperty(DefaultGradleVersion.VERSION_NUMBER_PROPERTY);
            return GradleVersion.version(versionString);
        } finally {
            in.close();
        }
    }

    private List<File> listDirs(File baseDir) {
        return listFiles(baseDir, directoryFileFilter());
    }

    private List<File> listFiles(File baseDir) {
        return listFiles(baseDir, null);
    }

    private List<File> listFiles(File baseDir, @Nullable FileFilter filter) {
        File[] dirs = baseDir.listFiles(filter);
        return dirs == null ? Collections.<File>emptyList() : Arrays.asList(dirs);
    }

}
