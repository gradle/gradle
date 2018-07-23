/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.initialization.loadercache;

import org.gradle.api.internal.changedetection.state.ClasspathFingerprinter;
import org.gradle.api.internal.file.collections.ImmutableFileCollection;
import org.gradle.caching.internal.BuildCacheHasher;
import org.gradle.caching.internal.DefaultBuildCacheHasher;
import org.gradle.internal.classloader.ClasspathHasher;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.hash.HashCode;
import org.gradle.normalization.internal.InputNormalizationStrategy;

public class DefaultClasspathHasher implements ClasspathHasher {

    private final ClasspathFingerprinter fingerprinter;

    public DefaultClasspathHasher(ClasspathFingerprinter fingerprinter) {
        this.fingerprinter = fingerprinter;
    }

    @Override
    public HashCode hash(ClassPath classpath) {
        CurrentFileCollectionFingerprint fingerprint = fingerprinter.fingerprint(ImmutableFileCollection.of(classpath.getAsFiles()), InputNormalizationStrategy.NO_NORMALIZATION);
        BuildCacheHasher hasher = new DefaultBuildCacheHasher();
        hasher.putHash(fingerprint.getHash());
        return hasher.hash();
    }
}
