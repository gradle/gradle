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

import com.google.common.collect.Sets;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.RegexFileFilter;
import org.gradle.internal.Try;
import org.gradle.util.GradleVersion;

import java.io.File;
import java.io.FileFilter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.SortedSet;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

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
        final SortedSet<VersionSpecificCacheDirectory> result = Sets.newTreeSet();
        for (final File subDir : listVersionSpecificCacheDirs()) {
            Try<GradleVersion> version = tryParseGradleVersion(subDir);
            version.ifSuccessful(new Consumer<GradleVersion>() {
                @Override
                public void accept(GradleVersion gradleVersion) {
                    result.add(new VersionSpecificCacheDirectory(subDir, gradleVersion));
                }
            });
        }
        return result;
    }

    private Collection<File> listVersionSpecificCacheDirs() {
        FileFilter combinedFilter = FileFilterUtils.and(directoryFileFilter(), new RegexFileFilter("^\\d.*"));
        File[] result = baseDir.listFiles(combinedFilter);
        return result == null ? Collections.<File>emptySet() : Arrays.asList(result);
    }

    private Try<GradleVersion> tryParseGradleVersion(final File dir) {
        return Try.ofFailable(new Callable<GradleVersion>() {
            @Override
            public GradleVersion call() {
                return GradleVersion.version(dir.getName());
            }
        });
    }
}
