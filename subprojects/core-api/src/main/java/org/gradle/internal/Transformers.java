/*
 * Copyright 2022 the original author or authors.
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
import org.gradle.api.Transformer;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.regex.Pattern;

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
            @Override
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

        @Override
        public O transform(I input) {
            return Cast.cast(outputType, input);
        }
    }

    public static <T> Transformer<String, T> asString() {
        return new ToStringTransformer<T>();
    }

    private static class ToStringTransformer<T> implements Transformer<String, T> {
        @Override
        public String transform(T original) {
            return original == null ? null : original.toString();
        }
    }

    /**
     * Transforms strings which may have spaces and which may have already been escaped with
     * quotes into safe command-line arguments.
     */
    public static Transformer<String, String> asSafeCommandLineArgument() {
        return new CommandLineArgumentTransformer();
    }

    private static class CommandLineArgumentTransformer implements Transformer<String, String> {
        private static final Pattern SINGLE_QUOTED = Pattern.compile("^'.*'$");
        private static final Pattern DOUBLE_QUOTED = Pattern.compile("^\".*\"$");
        private static final Pattern A_SINGLE_QUOTE =  Pattern.compile("'");

        @Override
        public String transform(String input) {
            if (SINGLE_QUOTED.matcher(input).matches() || DOUBLE_QUOTED.matcher(input).matches() || !input.contains(" ")) {
                return input;
            } else {
                return wrapWithSingleQuotes(input);
            }
        }

        private String wrapWithSingleQuotes(String input) {
            return String.format("'%1$s'", escapeSingleQuotes(input));
        }

        private String escapeSingleQuotes(String input) {
            return A_SINGLE_QUOTE.matcher(input).replaceAll("\\\\'");
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
            @Override
            public Class<T> transform(T original) {
                @SuppressWarnings("unchecked")
                Class<T> aClass = (Class<T>) original.getClass();
                return aClass;
            }
        };
    }

    public static <R> Transformer<R, Object> toTransformer(final Factory<R> factory) {
        return new Transformer<R, Object>() {
            @Override
            public R transform(Object original) {
                return factory.create();
            }
        };
    }

    public static <R, I> Transformer<R, I> toTransformer(final Action<? super I> action) {
        return new Transformer<R, I>() {
            @Override
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
            @Override
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
            @Override
            public T transform(I original) {
                return t;
            }
        };
    }
}
