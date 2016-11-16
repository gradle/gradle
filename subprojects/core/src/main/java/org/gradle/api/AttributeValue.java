/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.api;

import org.gradle.internal.Cast;
import org.gradle.internal.Factory;

import java.util.NoSuchElementException;

/**
 * Represents an optional attribute value, as found in an attribute container. There are 3 possible cases:
 * <ul>
 *     <li><i>present</i> is the default, and represents an attribute with an actual value</li>
 *     <li><i>missing</i> used whenever an attribute is known of the {@link AttributesSchema} of a consumer,
 *     no value was provided.</li>
 *     <li><i>unknown</i> used whenever an attribute is unknown of the {@link AttributesSchema} of a consumer,
 *     implying that no value was provided. It is different from the missing case in the sense that the consumer
 *     had no chance to provide a value here.</li>
 * </ul>
 * During attribute matching, this can be used to implement various {@link AttributeMatchingStrategy strategies}.
 * @param <T> the type of the attribute
 */
public class AttributeValue<T> {
    private final static AttributeValue<Object> MISSING = new AttributeValue<Object>(null) {
        @Override
        public boolean isMissing() {
            return true;
        }
    };
    private final static AttributeValue<Object> UNKNOWN = new AttributeValue<Object>(null) {
        @Override
        public boolean isUnknown() {
            return true;
        }
    };

    private final T value;

    private AttributeValue(T value) {
        this.value = value;
    }

    /**
     * Creates a valued attribute from a non-null value.
     * @param value the value of the attribute
     * @param <T> the type of the attribute
     * @return a <i>present</i> attribute value
     */
    public static <T> AttributeValue<T> of(T value) {
        return new AttributeValue<T>(value);
    }

    /**
     * Creates a missing attribute value, used to represent the fact that the attribute is known
     * but the consumer didn't want to express a value for it (it doesn't care).
     * @param <T> the type of the attribute
     * @return a <i>missing</i> attribute value
     */
    public static <T> AttributeValue<T> missing() {
        return Cast.uncheckedCast(MISSING);
    }

    /**
     * Creates an unknown attribute value, used to represent the fact that the attribute is unknown
     * from the consumer side. Produces might want to do something differently in this case.
     * @param <T> the type of the attribute
     * @return an <i>unknown</i> attribute value
     */
    public static <T> AttributeValue<T> unknown() {
        return Cast.uncheckedCast(UNKNOWN);
    }

    /**
     * Tells if this attribute value is present.
     * @return true if this attribute value is present, implying not <code>null</code>.
     */
    public boolean isPresent() {
        return value != null;
    }

    /**
     * Returns the value of this attribute.
     * @return the value of this attribute. Throws an error if called on a missing or unknown attribute value.
     */
    public T get() {
        if (value == null) {
            throw new NoSuchElementException("No value provided");
        }
        return value;
    }

    /**
     * Returns true if this attribute value is unknown.
     * @return true if this attribute value is unknown.
     */
    public boolean isUnknown() {
        return false;
    }

    /**
     * Returns true if this attribute value is missing.
     * @return true if this attribute value is missing.
     */
    public boolean isMissing() {
        return false;
    }

    /**
     * Configures a value to return if this attribute value is present. The transformer instance is passed
     * the value of the attribute when called. This can be used to return a different value depending on
     * the state of the attribute, as in this example:
     * <p/>
     * <code>
     *     String result = attributeValue.whenPresent { String value ->
     *         value.toUpperCase()
     *     }.whenMissing {
     *         'default'
     *     }.whenUnknown {
     *         'unknown
     *     }.get()
     * </code>
     * <p/>
     * Alternatively if the missing and default value are the same, it is possible to use the shortcut {@link DefaultingResultBuilder#getOrElse} method:
     * <p/>
     * <code>
     *     String result = attributeValue.whenPresent { String value ->
     *         value.toUpperCase()
     *     }.getOrElse {
     *         'default'
     *     }
     * </code>
     * @param transformer the transformer
     * @param <O> the type of the result
     * @return a value producer
     */
    public <O> ResultBuilder<O> whenPresent(Transformer<O, T> transformer) {
        return new Builder<O>(transformer);
    }

    /**
     * Interface for builders supporting returning a default value in case value is missing or unknown.
     * @param <O> the type of the result.
     */
    public interface DefaultingResultBuilder<O> {
        /**
         * Calls the transformer if the attribute value is present. If the attribute value is missing and that
         * a missing generator was set, returns the generated missing value, otherwise calls the factory.
         * @param factory the code to call if we couldn't generate a value
         * @return the result of the generator
         */
        O getOrElse(Factory<O> factory);
    }

    /**
     * Builder for producers with a present value generator.
     * @param <O> the type of the result.
     */
    public interface ResultBuilder<O> extends DefaultingResultBuilder<O> {
        /**
         * Sets the factory that will be called if the attribute value is missing.
         * @param factory the factory
         * @return a builder allowing to set what happens if the value is unknown.
         */
        WithMissingResultBuilder<O> whenMissing(Factory<O> factory);
    }

    /**
     * Builder for producers with a present value and missing value generator.
     * @param <O> the type of the result.
     */
    public interface WithMissingResultBuilder<O> extends DefaultingResultBuilder<O> {
        /**
         * Sets the factory to be called if the attribute value is unknown.
         * @param factory the factory
         * @return a producer which can be called to get a transformed value.
         */
        ConfiguredResultBuilder<O> whenUnknown(Factory<O> factory);
    }

    /**
     * Interface for builders which support generating an output.
     * @param <O>
     */
    public interface ConfiguredResultBuilder<O> {
        /**
         * Generates a value depending on the attribute state. If is is present, it will call
         * the transformer used when attribute values are present. If it is missing, it will
         * call the missing generator, and if unknown, the unknown generator.
         * @return the generated value
         */
        O get();
    }

    private class Builder<O> implements ResultBuilder<O>, WithMissingResultBuilder<O>, ConfiguredResultBuilder<O> {

        private Transformer<O, T> whenPresent;
        private Factory<O> whenMissing;
        private Factory<O> whenUnknown;

        public Builder(Transformer<O, T> transformer) {
            this.whenPresent = transformer;
        }

        @Override
        public O getOrElse(Factory<O> factory) {
            // not delegating to getOrElse(O) to avoid computing the result if not necessary
            if (isPresent()) {
                return whenPresent.transform(AttributeValue.this.value);
            } else if (isMissing() && whenMissing != null) {
                return whenMissing.create();
            }
            return factory.create();
        }

        @Override
        public WithMissingResultBuilder<O> whenMissing(Factory<O> factory) {
            this.whenMissing = factory;
            return this;
        }

        @Override
        public ConfiguredResultBuilder<O> whenUnknown(Factory<O> factory) {
            this.whenUnknown = factory;
            return this;
        }

        @Override
        public O get() {
            if (value != null) {
                return whenPresent.transform(value);
            } else if (isMissing() && whenMissing != null) {
                return whenMissing.create();
            } else if (whenUnknown != null) {
                return whenUnknown.create();
            }
            return null;
        }
    }
}
