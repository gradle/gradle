/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.changedetection.state;

import com.google.common.hash.HashCode;
import org.gradle.api.file.RelativePath;
import org.gradle.caching.internal.BuildCacheHasher;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;

public class MetadataFilterAdapter implements ResourceHasher {
    private final MetadataFilter filter;
    private final ResourceHasher delegate;

    public MetadataFilterAdapter(MetadataFilter filter, ResourceHasher delegate) {
        this.filter = filter;
        this.delegate = delegate;
    }

    @Override
    public HashCode hash(RegularFileSnapshot fileSnapshot) {
        if (filter.shouldBeIgnored(fileSnapshot.getRelativePath())) {
            return null;
        }
        return delegate.hash(fileSnapshot);
    }

    @Override
    public HashCode hash(ZipEntry zipEntry, InputStream zipInput) throws IOException {
        if (filter.shouldBeIgnored(RelativePath.parse(true, zipEntry.getName()))) {
            return null;
        }
        return delegate.hash(zipEntry, zipInput);
    }

    @Override
    public void appendImplementationToHasher(BuildCacheHasher hasher) {
        hasher.putString(getClass().getName());
        filter.appendImplementationToHasher(hasher);
        delegate.appendImplementationToHasher(hasher);
    }
}
