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

import com.google.common.base.Preconditions;
import org.gradle.util.GradleVersion;

import javax.annotation.Nonnull;
import java.io.File;

public class VersionSpecificCacheDirectory implements Comparable<VersionSpecificCacheDirectory> {

    private final File dir;
    private final GradleVersion version;

    public VersionSpecificCacheDirectory(File dir, GradleVersion version) {
        this.dir = Preconditions.checkNotNull(dir, "dir must not be null");
        this.version = Preconditions.checkNotNull(version, "version must not be null");
    }

    public File getDir() {
        return dir;
    }

    public GradleVersion getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        VersionSpecificCacheDirectory that = (VersionSpecificCacheDirectory) o;
        return this.dir.equals(that.dir) && this.version.equals(that.version);
    }

    @Override
    public int hashCode() {
        int result = dir.hashCode();
        result = 31 * result + version.hashCode();
        return result;
    }

    @Override
    public int compareTo(@Nonnull VersionSpecificCacheDirectory that) {
        return this.version.compareTo(that.version);
    }

}
