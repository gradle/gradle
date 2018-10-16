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

package org.gradle.caching.internal.tasks;

import org.gradle.api.NonNullApi;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.snapshot.impl.ImplementationSnapshot;

import javax.annotation.Nullable;
import java.util.Collection;

@NonNullApi
public class DebuggingTaskOutputCachingBuildCacheKeyBuilder implements TaskOutputCachingBuildCacheKeyBuilder {
    private static final Logger LOGGER = Logging.getLogger(DebuggingTaskOutputCachingBuildCacheKeyBuilder.class);

    private final TaskOutputCachingBuildCacheKeyBuilder delegate;

    public DebuggingTaskOutputCachingBuildCacheKeyBuilder(TaskOutputCachingBuildCacheKeyBuilder delegate) {
        this.delegate = delegate;
    }

    @Override
    public void appendTaskImplementation(ImplementationSnapshot taskImplementation) {
        log("taskImplementation", taskImplementation);
        delegate.appendTaskImplementation(taskImplementation);
    }

    @Override
    public void appendTaskActionImplementations(Collection<ImplementationSnapshot> taskActionImplementations) {
        for (ImplementationSnapshot actionImpl : taskActionImplementations) {
            log("actionImplementation", actionImpl);
        }
        delegate.appendTaskActionImplementations(taskActionImplementations);
    }

    @Override
    public void appendInputValuePropertyHash(String propertyName, HashCode hashCode) {
        LOGGER.lifecycle("Appending inputValuePropertyHash for '{}' to build cache key: {}", propertyName, hashCode);
        delegate.appendInputValuePropertyHash(propertyName, hashCode);
    }

    @Override
    public void appendInputFilesProperty(String propertyName, CurrentFileCollectionFingerprint fileCollectionFingerprint) {
        LOGGER.lifecycle("Appending inputFilePropertyHash for '{}' to build cache key: {}", propertyName, fileCollectionFingerprint.getHash());
        delegate.appendInputFilesProperty(propertyName, fileCollectionFingerprint);

    }

    @Override
    public void inputPropertyNotCacheable(String propertyName, String nonCacheableReason) {
        LOGGER.lifecycle("Non-cacheable inputs: property '{}' {}.", propertyName, nonCacheableReason);
        delegate.inputPropertyNotCacheable(propertyName, nonCacheableReason);
    }

    @Override
    public void appendOutputPropertyName(String propertyName) {
        log("outputPropertyName", propertyName);
        delegate.appendOutputPropertyName(propertyName);
    }

    @Override
    public TaskOutputCachingBuildCacheKey build() {
        return delegate.build();
    }

    private void log(String name, @Nullable Object value) {
        LOGGER.lifecycle("Appending {} to build cache key: {}", name, value);
    }

}
