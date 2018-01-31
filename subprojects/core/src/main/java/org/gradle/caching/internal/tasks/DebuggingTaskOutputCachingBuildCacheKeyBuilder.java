package org.gradle.caching.internal.tasks;

import org.gradle.api.NonNullApi;
import org.gradle.api.internal.changedetection.state.ImplementationSnapshot;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.hash.HashCode;

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
        log("taskClass", taskImplementation.getTypeName());
        if (!taskImplementation.hasUnknownClassLoader()) {
            log("classLoaderHash", taskImplementation.getClassLoaderHash());
        }
        delegate.appendTaskImplementation(taskImplementation);
    }

    @Override
    public void appendTaskActionImplementations(Collection<ImplementationSnapshot> taskActionImplementations) {
        for (ImplementationSnapshot actionImpl : taskActionImplementations) {
            log("actionType", actionImpl.getTypeName());
            log("actionClassLoaderHash", actionImpl.hasUnknownClassLoader() ? null : actionImpl.getClassLoaderHash());
        }
        delegate.appendTaskActionImplementations(taskActionImplementations);
    }

    @Override
    public void appendInputPropertyHash(String propertyName, HashCode hashCode) {
        LOGGER.lifecycle("Appending inputPropertyHash for '{}' to build cache key: {}", propertyName, hashCode);
        delegate.appendInputPropertyHash(propertyName, hashCode);
    }

    @Override
    public void inputPropertyLoadedByUnknownClassLoader(String propertyName) {
        String sanitizedPropertyName = DefaultTaskOutputCachingBuildCacheKeyBuilder.sanitizeImplementationPropertyName(propertyName);
        LOGGER.lifecycle("The implementation of '{}' cannot be determined, because it was loaded by an unknown classloader", sanitizedPropertyName);
        delegate.inputPropertyLoadedByUnknownClassLoader(propertyName);
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
