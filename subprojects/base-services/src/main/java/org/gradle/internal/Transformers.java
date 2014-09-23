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

package org.gradle.internal;

import org.gradle.api.Action;
import org.gradle.api.Named;
import org.gradle.api.Namer;
import org.gradle.api.Transformer;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

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

    //just returns the original object
    public static <T> Transformer<T, T> noOpTransformer() {
        return new Transformer<T, T>() {
            public T transform(T original) {
                return original;
            }
        };
    }

    private static class CastingTransformer<O, I> implements Transformer<O, I> {
        final Class<O> outputType;

        public CastingTransformer(Class<O> outputType) {
            this.outputType = outputType;
        }

        public O transform(I input) {
            return Cast.cast(outputType, input);
        }
    }

    public static <T> Transformer<String, T> asString() {
        return new ToStringTransformer<T>();
    }

    private static class ToStringTransformer<T> implements Transformer<String, T> {
        public String transform(T original) {
            return original == null ? null : original.toString();
        }
    }

    /**
     * Returns a transformer that names {@link Named} objects.
     *
     * Nulls are returned as null.
     *
     * @return The naming transformer.
     */
    public static Transformer<String, Named> name() {
        return name(new Named.Namer());
    }

    /**
     * Returns a transformer that names objects with the given {@link Namer}
     * @param namer The namer to name the objects with
     * @param <T> The type of objects to be named
     * @return The naming transformer.
     */
    public static <T> Transformer<String, T> name(Namer<? super T> namer) {
        return new ToNameTransformer<T>(namer);
    }

    private static class ToNameTransformer<T> implements Transformer<String, T> {
        private final Namer<? super T> namer;

        public ToNameTransformer(Namer<? super T> namer) {
            this.namer = namer;
        }

        public String transform(T thing) {
            return thing == null ? null : namer.determineName(thing);
        }
    }

    /**
     * A getClass() transformer.
     *
     * @param <T> The type of the object
     * @return A getClass() transformer.
     */
    public static <T> Transformer<Class<T>, T> type() {
        return new Transformer<Class<T>, T>() {
            public Class<T> transform(T original) {
                @SuppressWarnings("unchecked")
                Class<T> aClass = (Class<T>) original.getClass();
                return aClass;
            }
        };
    }

    public static <R> Transformer<R, Object> toTransformer(final Factory<R> factory) {
        return new Transformer<R, Object>() {
            public R transform(Object original) {
                return factory.create();
            }
        };
    }

    public static <R, I> Transformer<R, I> toTransformer(final Action<? super I> action) {
        return new Transformer<R, I>() {
            public R transform(I original) {
                action.execute(original);
                return null;
            }
        };
    }

    /**
     * Converts a URL to a URI
     */
    public static Transformer<URL, URI> toURL() {
        return new Transformer<URL, URI>() {
            public URL transform(URI original) {
                try {
                    return original.toURL();
                } catch (MalformedURLException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            }
        };
    }

    /**
     * Always returns the given argument.
     */
    public static <T, I> Transformer<T, I> constant(final T t) {
        return new Transformer<T, I>() {
            public T transform(I original) {
                return t;
            }
        };
    }
}
