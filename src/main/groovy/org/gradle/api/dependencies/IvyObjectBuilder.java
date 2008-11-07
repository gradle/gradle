package org.gradle.api.dependencies;

import org.gradle.api.Transformer;
import groovy.lang.Closure;

/**
 * <p>A {@code IvyObjectBuilder} builds Ivy domain objects of type T. You can influence the construction of the Ivy
 * objects by adding transformers to this builder. A transformer can either be a closure, or a {@link
 * org.gradle.api.Transformer} implementation. The transformers are called in the order added.</p>
 */
public interface IvyObjectBuilder<T> {
    /**
     * <p>Adds a transformer to this builder.</p>
     *
     * @param transformer The transformer to add.
     */
    void addIvyTransformer(Transformer<T> transformer);

    /**
     * <p>Adds a transformation closure to this builder. The closure is passed the object to transform as a parameter.
     * The closure can return an object of type T, which will be used as the transformed value. The object to transform
     * is also set as the delegate of the closure.</p>
     *
     * @param transformer The transformation closure to add.
     */
    void addIvyTransformer(Closure transformer);
}
