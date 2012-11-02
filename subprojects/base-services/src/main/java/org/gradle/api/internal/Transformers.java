/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal;

import org.gradle.api.Transformer;

/**
 * Utility transformers.
 */
public abstract class Transformers {

    /**
     * Creates a transformer that simply type casts the input to the given output type.
     *
     * @param outputType The type to cast the input to
     * @param <O> The type of the transformed object
     * @param <I> The type of the object to be transformed
     * @return A transformer that simply type casts the input to the given output type.
     */
    public static <O, I> Transformer<O, I> cast(Class<O> outputType) {
        return new CastingTransformer<O, I>(outputType);
    }

    /**
     * Immediately casts the input using a transformer returned by {@link #cast(Class)}.
     *
     * @param outputType The type to cast the input to
     * @param input The object to be cast
     * @param <O> The type of the transformed object
     * @param <I> The type of the object to be transformed
     * @return The input object, cast to the output type
     */
    public static <O, I> O cast(Class<O> outputType, I input) {
        return cast(outputType).transform(input);
    }

    private static class CastingTransformer<O, I> implements Transformer<O, I> {
        final Class<O> outputType;

        public CastingTransformer(Class<O> outputType) {
            this.outputType = outputType;
        }

        public O transform(I input) {
            try {
                return outputType.cast(input);
            } catch (ClassCastException e) {
                throw new ClassCastException(String.format(
                   "Failed to cast object %s of type %s to target type %s", input, input.getClass().getName(), outputType.getName()
                ));
            }
        }
    }

    public static Transformer<String, Object> asString() {
        return new ToStringTransformer();
    }

    private static class ToStringTransformer implements Transformer<String, Object> {
        public String transform(Object original) {
            return original == null ? null : original.toString();
        }
    }

}
