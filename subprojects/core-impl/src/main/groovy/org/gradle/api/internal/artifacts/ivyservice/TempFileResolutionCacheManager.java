/*
 * Copyright 2011 the original author or authors.
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
import org.apache.ivy.core.cache.ResolutionCacheManager;
import org.apache.ivy.core.module.id.ModuleRevisionId;

import java.io.File;
import java.io.IOException;

class TempFileResolutionCacheManager implements ResolutionCacheManager {
    private static final String FILE_PATTERN = "resolved-[organisation]-[module]-[revision]";

    private File cacheTempDir;

    TempFileResolutionCacheManager(File cacheDir) {
        cacheTempDir = new File(cacheDir, "tmp");
        cacheTempDir.mkdirs();
    }

    public File getResolutionCacheRoot() {
        return cacheTempDir;
    }

    public File getResolvedIvyFileInCache(ModuleRevisionId mrid) {
        String file = IvyPatternHelper.substitute(FILE_PATTERN, mrid);
        return tmpFile(file, ".xml");
    }

    public File getResolvedIvyPropertiesInCache(ModuleRevisionId mrid) {
        String file = IvyPatternHelper.substitute(FILE_PATTERN, mrid);
        return tmpFile(file, ".properties");
    }

    public File getConfigurationResolveReportInCache(String resolveId, String conf) {
        return tmpFile(resolveId + "-" + conf, ".xml");
    }

    public File[] getConfigurationResolveReportsInCache(String resolveId) {
        return new File[0];
    }

    public void clean() {
        throw new UnsupportedOperationException();
    }

    private File tmpFile(String prefix, String suffix) {
        try {
            File xml = File.createTempFile(prefix, suffix, cacheTempDir);
            xml.deleteOnExit();
            return xml;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
