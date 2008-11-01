package org.gradle.api.internal.dependencies;

import org.gradle.api.internal.Transformer;
import groovy.lang.Closure;

/**
 * Contributes some element of type T to an ivy module descriptor.
 */
public interface ModuleDescriptorContributor<T> {
    void addIvyTransformer(Transformer<T> transformer);

    void addIvyTransformer(Closure transformer);
}
