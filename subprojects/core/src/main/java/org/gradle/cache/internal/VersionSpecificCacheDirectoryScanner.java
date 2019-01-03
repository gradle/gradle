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

import com.google.common.collect.ImmutableSortedSet;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.RegexFileFilter;
import org.gradle.util.GradleVersion;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileFilter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.SortedSet;

import static org.apache.commons.io.filefilter.FileFilterUtils.directoryFileFilter;

public class VersionSpecificCacheDirectoryScanner {

    private final File baseDir;

    public VersionSpecificCacheDirectoryScanner(File baseDir) {
        this.baseDir = baseDir;
    }

    public File getBaseDir() {
        return baseDir;
    }

    public File getDirectory(GradleVersion gradleVersion) {
        return new File(baseDir, gradleVersion.getVersion());
    }

    public SortedSet<VersionSpecificCacheDirectory> getExistingDirectories() {
        ImmutableSortedSet.Builder<VersionSpecificCacheDirectory> builder = ImmutableSortedSet.naturalOrder();
        for (File subDir : listVersionSpecificCacheDirs()) {
            GradleVersion version = tryParseGradleVersion(subDir);
            if (version != null) {
                builder.add(new VersionSpecificCacheDirectory(subDir, version));
            }
        }
        return builder.build();
    }

    private Collection<File> listVersionSpecificCacheDirs() {
        FileFilter combinedFilter = FileFilterUtils.and(directoryFileFilter(), new RegexFileFilter("^\\d.*"));
        File[] result = baseDir.listFiles(combinedFilter);
        return result == null ? Collections.<File>emptySet() : Arrays.asList(result);
    }

    @Nullable
    private GradleVersion tryParseGradleVersion(File dir) {
        try {
            return GradleVersion.version(dir.getName());
        } catch (Exception e) {
            return null;
        }
    }
}
