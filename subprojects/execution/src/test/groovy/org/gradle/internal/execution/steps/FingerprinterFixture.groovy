/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.execution.steps

import com.google.common.collect.ImmutableSortedMap
import org.gradle.api.internal.cache.StringInterner
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.file.collections.ImmutableFileCollection
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint
import org.gradle.internal.fingerprint.impl.AbsolutePathFileCollectionFingerprinter
import org.gradle.internal.hash.TestFileHasher
import org.gradle.internal.snapshot.WellKnownFileLocations
import org.gradle.internal.snapshot.impl.DefaultFileSystemMirror
import org.gradle.internal.snapshot.impl.DefaultFileSystemSnapshotter
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider

abstract trait FingerprinterFixture {
    abstract TestNameTestDirectoryProvider getTemporaryFolder()

    final fingerprinter = new AbsolutePathFileCollectionFingerprinter(
        new DefaultFileSystemSnapshotter(
            new TestFileHasher(),
            new StringInterner(),
            TestFiles.fileSystem(),
            new DefaultFileSystemMirror(new NoWellKnownFileLocations())
        )
    )

    ImmutableSortedMap<String, CurrentFileCollectionFingerprint> fingerprintsOf(Map<String, Object> properties) {
        def builder = ImmutableSortedMap.<String, CurrentFileCollectionFingerprint>naturalOrder()
        properties.each { propertyName, value ->
            def files = (value instanceof Iterable
                ? (Collection<?>) value
                : [value]).collect {
                it instanceof File
                    ? it
                    : temporaryFolder.file(it)
            }
            builder.put(propertyName, fingerprinter.fingerprint(ImmutableFileCollection.of(files)))
        }
        return builder.build()
    }

    private static class NoWellKnownFileLocations implements WellKnownFileLocations {
        @Override
        boolean isImmutable(String path) {
            return false
        }
    }
}
