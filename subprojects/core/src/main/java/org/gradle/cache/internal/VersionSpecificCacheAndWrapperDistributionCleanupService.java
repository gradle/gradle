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

import com.google.common.collect.ImmutableList;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.RegexFileFilter;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.time.Time;
import org.gradle.internal.time.Timer;
import org.gradle.util.GradleVersion;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.apache.commons.io.filefilter.FileFilterUtils.directoryFileFilter;
import static org.gradle.api.internal.changedetection.state.CrossBuildFileHashCache.FILE_HASHES_CACHE_KEY;

public class VersionSpecificCacheAndWrapperDistributionCleanupService implements Stoppable {

    static final String MARKER_FILE_PATH = FILE_HASHES_CACHE_KEY + "/" + FILE_HASHES_CACHE_KEY + ".lock";
    private static final Logger LOGGER = Logging.getLogger(VersionSpecificCacheAndWrapperDistributionCleanupService.class);
    private static final long MAX_UNUSED_DAYS = 30;
    private static final ImmutableList<String> DISTRIBUTION_TYPES = ImmutableList.of("bin", "all");

    private final GradleVersion currentVersion;
    private final File gradleUserHomeDirectory;

    public VersionSpecificCacheAndWrapperDistributionCleanupService(GradleVersion currentVersion, File gradleUserHomeDirectory) {
        this.currentVersion = currentVersion;
        this.gradleUserHomeDirectory = gradleUserHomeDirectory;
    }

    @Override
    public void stop() {
        Timer timer = Time.startTimer();
        long minimumTimestamp = Math.max(0, System.currentTimeMillis() - TimeUnit.DAYS.toMillis(MAX_UNUSED_DAYS));
        for (File subDir : listVersionSpecificCacheDirs()) {
            try {
                processCacheSubDirectory(subDir, minimumTimestamp);
            } catch (Exception e) {
                LOGGER.error("Failed to process/clean up version-specific cache directory: {}", subDir, e);
            }
        }
        LOGGER.debug("Processed version-specific caches for cleanup in {}", timer.getElapsed());
    }

    private Collection<File> listVersionSpecificCacheDirs() {
        FileFilter combinedFilter = FileFilterUtils.and(directoryFileFilter(), new RegexFileFilter("^\\d.*"));
        File cachesDir = new File(gradleUserHomeDirectory, DefaultCacheScopeMapping.GLOBAL_CACHE_DIR_NAME);
        File[] result = cachesDir.listFiles(combinedFilter);
        return result == null ? Collections.<File>emptySet() : Arrays.asList(result);
    }

    private void processCacheSubDirectory(File dir, long minimumTimestamp) throws Exception {
        GradleVersion version;
        try {
            version = GradleVersion.version(dir.getName());
        } catch (Exception e) {
            LOGGER.debug("Ignoring directory with unparsable version: {}", dir, e);
            return;
        }
        processVersionSpecificCacheDir(dir, version, minimumTimestamp);
    }

    private void processVersionSpecificCacheDir(File dir, GradleVersion version, long minimumTimestamp) throws IOException {
        if (version.compareTo(currentVersion) < 0) {
            if (shouldDelete(dir, minimumTimestamp)) {
                LOGGER.debug("Deleting version-specific cache directory for {} at {}", version, dir);
                FileUtils.deleteDirectory(dir);
                deleteDistributions(version);
            }
        }
    }

    private boolean shouldDelete(File dir, long minimumTimestamp) {
        File markerFile = new File(dir, MARKER_FILE_PATH);
        return markerFile.exists() && markerFile.lastModified() < minimumTimestamp;
    }

    private void deleteDistributions(GradleVersion version) throws IOException {
        File distsDir = new File(gradleUserHomeDirectory, "wrapper/dists");
        for (String distributionType : DISTRIBUTION_TYPES) {
            File dir = new File(distsDir, "gradle-" + version.getVersion() + "-" + distributionType);
            if (dir.isDirectory()) {
                LOGGER.info("Deleting Gradle distribution at {}", dir);
                FileUtils.deleteDirectory(dir);
            }
        }
    }
}
