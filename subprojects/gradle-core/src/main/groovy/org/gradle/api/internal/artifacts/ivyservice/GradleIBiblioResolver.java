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
package org.gradle.api.internal.artifacts.ivyservice;

import org.apache.ivy.core.IvyPatternHelper;
import org.apache.ivy.core.cache.DefaultRepositoryCacheManager;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.resolve.ResolveData;
import org.apache.ivy.core.resolve.ResolvedModuleRevision;
import org.apache.ivy.plugins.resolver.IBiblioResolver;
import org.gradle.util.UncheckedException;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Calendar;
import java.util.Date;

/**
 * @author Hans Dockter
 */
public class GradleIBiblioResolver extends IBiblioResolver {
    public static final CacheTimeoutStrategy NEVER = new CacheTimeoutStrategy() {
        public boolean isCacheTimedOut(long lastResolvedTime) {
            return false;
        }
    };

    public static final CacheTimeoutStrategy ALWAYS = new CacheTimeoutStrategy() {
        public boolean isCacheTimedOut(long lastResolvedTime) {
            return true;
        }
    };

    public static final CacheTimeoutStrategy DAILY = new CacheTimeoutStrategy() {
        public boolean isCacheTimedOut(long lastResolvedTime) {
            Calendar calendarCurrent = Calendar.getInstance();
            calendarCurrent.setTime(new Date());
            int dayOfYear = calendarCurrent.get(Calendar.DAY_OF_YEAR);
            int year = calendarCurrent.get(Calendar.YEAR);

            Calendar calendarLastResolved = Calendar.getInstance();
            calendarLastResolved.setTime(new Date(lastResolvedTime));
            if (calendarLastResolved.get(Calendar.YEAR) == year && calendarLastResolved.get(Calendar.DAY_OF_YEAR)
                    == dayOfYear) {
                return false;
            }
            return true;
        }
    };

    private CacheTimeoutStrategy snapshotTimeout = DAILY;

    /**
     * Returns the timeout strategy for a Maven Snapshot in the cache
     */
    public CacheTimeoutStrategy getSnapshotTimeout() {
        return snapshotTimeout;
    }

    /**
     * Sets the time in ms a Maven Snapshot in the cache is not checked for a newer version
     *
     * @param snapshotLifetime The lifetime in ms
     */
    public void setSnapshotTimeout(long snapshotLifetime) {
        this.snapshotTimeout = new Interval(snapshotLifetime);
    }

    /**
     * Sets a timeout strategy for a Maven Snapshot in the cache
     *
     * @param cacheTimeoutStrategy The strategy
     */
    public void setSnapshotTimeout(CacheTimeoutStrategy cacheTimeoutStrategy) {
        this.snapshotTimeout = cacheTimeoutStrategy;
    }

    @Override
    public void setRoot(String root) {
        super.setRoot(root);
        URI rootUri;
        try {
            rootUri = new URI(root);
        } catch (URISyntaxException e) {
            throw UncheckedException.asUncheckedException(e);
        }
        if (rootUri.getScheme().equalsIgnoreCase("file")) {
            setSnapshotTimeout(ALWAYS);
        } else {
            setSnapshotTimeout(DAILY);
        }
    }

    @Override
    protected ResolvedModuleRevision findModuleInCache(DependencyDescriptor dd, ResolveData data) {
        setChangingPattern(null);
        ResolvedModuleRevision moduleRevision = super.findModuleInCache(dd, data);
        if (moduleRevision == null) {
            setChangingPattern(".*-SNAPSHOT");
            return null;
        }
        PropertiesFile cacheProperties = getCacheProperties(dd, moduleRevision);
        Long lastResolvedTime = getLastResolvedTime(cacheProperties);
        updateCachePropertiesToCurrentTime(cacheProperties);
        if (snapshotTimeout.isCacheTimedOut(lastResolvedTime)) {
            setChangingPattern(".*-SNAPSHOT");
            return null;
        } else {
            return moduleRevision;
        }
    }

    private void updateCachePropertiesToCurrentTime(PropertiesFile cacheProperties) {
        cacheProperties.setProperty("resolved.time", "" + System.currentTimeMillis());
        cacheProperties.save();
    }

    private long getLastResolvedTime(PropertiesFile cacheProperties) {
        String lastResolvedProp = cacheProperties.getProperty("resolved.time");
        if (lastResolvedProp != null) {
            return Long.parseLong(lastResolvedProp);
        }
        // No resolved.time property - assume that the properties file modification time == the resolve time
        return cacheProperties.file.lastModified();
    }

    private PropertiesFile getCacheProperties(DependencyDescriptor dd, ResolvedModuleRevision moduleRevision) {
        DefaultRepositoryCacheManager cacheManager = (DefaultRepositoryCacheManager) getRepositoryCacheManager();
        PropertiesFile props = new PropertiesFile(new File(cacheManager.getRepositoryCacheRoot(),
                IvyPatternHelper.substitute(cacheManager.getDataFilePattern(), moduleRevision.getId())),
                "ivy cached data file for " + dd.getDependencyRevisionId());
        return props;
    }

    public interface CacheTimeoutStrategy {
        boolean isCacheTimedOut(long lastResolvedTime);
    }

    private static class PropertiesFile extends org.apache.ivy.util.PropertiesFile {
        private final File file;

        private PropertiesFile(File file, String header) {
            super(file, header);
            this.file = file;
        }
    }

    public static class Interval implements CacheTimeoutStrategy {
        private long interval;

        public Interval(long interval) {
            this.interval = interval;
        }

        public boolean isCacheTimedOut(long lastResolvedTime) {
            return System.currentTimeMillis() - lastResolvedTime > interval;
        }
    }
}


