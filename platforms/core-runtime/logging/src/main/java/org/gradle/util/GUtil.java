/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.util;

import org.apache.commons.lang.StringUtils;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.specs.Spec;
import org.gradle.internal.Cast;
import org.gradle.internal.Factory;
import org.gradle.internal.InternalTransformer;
import org.gradle.internal.IoActions;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.io.StreamByteBuffer;
import org.gradle.util.internal.CollectionUtils;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;

/**
 * This class is only here to maintain binary compatibility with existing plugins.
 * <p>
 * Plugins should prefer external collection frameworks over this class.
 * Internally, all code should use {@link org.gradle.util.internal.GUtil}.
 *
 * @deprecated Will be removed in Gradle 9.0.
 */
@Deprecated
public class GUtil {

    static {
        DeprecationLogger.deprecateType(GUtil.class)
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(7, "org_gradle_util_reports_deprecations")
            .nagUser();
    }

    private static final Pattern WORD_SEPARATOR = Pattern.compile("\\W+");
    private static final Pattern UPPER_LOWER = Pattern.compile("(?m)([A-Z]*)([a-z0-9]*)");

    public GUtil() {}

    public static <T extends Collection<?>> T flatten(Object[] elements, T addTo, boolean flattenMaps) {
        return flatten(asList(elements), addTo, flattenMaps);
    }

    public static <T extends Collection<?>> T flatten(Object[] elements, T addTo) {
        return flatten(asList(elements), addTo);
    }

    public static <T extends Collection<?>> T flatten(Collection<?> elements, T addTo) {
        return flatten(elements, addTo, true);
    }

    @SuppressWarnings("TypeParameterUnusedInFormals")
    public static <T extends Collection<?>> T flattenElements(Object... elements) {
        Collection<T> out = new LinkedList<T>();
        flatten(elements, out, true);
        return Cast.uncheckedNonnullCast(out);
    }

    public static <T extends Collection<?>> T flatten(Collection<?> elements, T addTo, boolean flattenMapsAndArrays) {
        return flatten(elements, addTo, flattenMapsAndArrays, flattenMapsAndArrays);
    }

    public static <T extends Collection<?>> T flatten(Collection<?> elements, T addTo, boolean flattenMaps, boolean flattenArrays) {
        Iterator<?> iter = elements.iterator();
        while (iter.hasNext()) {
            Object element = iter.next();
            if (element instanceof Collection) {
                flatten((Collection<?>) element, addTo, flattenMaps, flattenArrays);
            } else if ((element instanceof Map) && flattenMaps) {
                flatten(((Map<?, ?>) element).values(), addTo, flattenMaps, flattenArrays);
            } else if (element.getClass().isArray() && flattenArrays) {
                flatten(asList((Object[]) element), addTo, flattenMaps, flattenArrays);
            } else {
                Cast.<Collection<Object>>uncheckedNonnullCast(addTo).add(element);
            }
        }
        return addTo;
    }

    /**
     * Flattens input collections (including arrays *but* not maps). If input is not a collection wraps it in a collection and returns it.
     *
     * @param input any object
     * @return collection of flattened input or single input wrapped in a collection.
     */
    @SuppressWarnings("MixedMutabilityReturnType")
    public static Collection<?> collectionize(Object input) {
        if (input == null) {
            return emptyList();
        } else if (input instanceof Collection) {
            Collection<?> out = new LinkedList<Object>();
            flatten((Collection<?>) input, out, false, true);
            return out;
        } else if (input.getClass().isArray()) {
            Collection<?> out = new LinkedList<Object>();
            flatten(asList((Object[]) input), out, false, true);
            return out;
        } else {
            return asList(input);
        }
    }

    public static List<Object> flatten(Collection<Object> elements, boolean flattenMapsAndArrays) {
        return flatten(elements, new ArrayList<Object>(), flattenMapsAndArrays);
    }

    public static List<Object> flatten(Collection<Object> elements) {
        return flatten(elements, new ArrayList<Object>());
    }

    public static String asPath(Iterable<?> collection) {
        return CollectionUtils.join(File.pathSeparator, collection);
    }

    public static List<String> prefix(String prefix, Collection<String> strings) {
        List<String> prefixed = new ArrayList<String>();
        for (String string : strings) {
            prefixed.add(prefix + string);
        }
        return prefixed;
    }

    public static boolean isTrue(@Nullable Object object) {
        if (object == null) {
            return false;
        }
        if (object instanceof Collection) {
            return ((Collection) object).size() > 0;
        } else if (object instanceof String) {
            return ((String) object).length() > 0;
        }
        return true;
    }

    /**
     * Prefer {@link #getOrDefault(Object, Factory)} if the value is expensive to compute or
     * would trigger early configuration.
     */
    @Nullable
    public static <T> T elvis(@Nullable T object, @Nullable T defaultValue) {
        return isTrue(object) ? object : defaultValue;
    }

    @Nullable
    public static <T> T getOrDefault(@Nullable T object, Factory<T> defaultValueSupplier) {
        return isTrue(object) ? object : defaultValueSupplier.create();
    }

    public static <V, T extends Collection<? super V>> T addToCollection(T dest, boolean failOnNull, Iterable<? extends V> src) {
        for (V v : src) {
            if (failOnNull && v == null) {
                throw new IllegalArgumentException("Illegal null value provided in this collection: " + src);
            }
            dest.add(v);
        }
        return dest;
    }

    public static <V, T extends Collection<? super V>> T addToCollection(T dest, Iterable<? extends V> src) {
        return addToCollection(dest, false, src);
    }

    @Deprecated
    @SuppressWarnings("unchecked")
    public static <V, T extends Collection<? super V>> T addToCollection(T dest, boolean failOnNull, Iterable<? extends V>... srcs) {
        for (Iterable<? extends V> src : srcs) {
            for (V v : src) {
                if (failOnNull && v == null) {
                    throw new IllegalArgumentException("Illegal null value provided in this collection: " + src);
                }
                dest.add(v);
            }
        }
        return dest;
    }

    @Deprecated
    @SuppressWarnings("unchecked")
    public static <V, T extends Collection<? super V>> T addToCollection(T dest, Iterable<? extends V>... srcs) {
        return addToCollection(dest, false, srcs);
    }

    public static Comparator<String> caseInsensitive() {
        return new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                int diff = o1.compareToIgnoreCase(o2);
                if (diff != 0) {
                    return diff;
                }
                return o1.compareTo(o2);
            }
        };
    }

    public static <K, V> Map<K, V> addMaps(Map<K, V> map1, Map<K, V> map2) {
        HashMap<K, V> map = new HashMap<K, V>();
        map.putAll(map1);
        map.putAll(map2);
        return map;
    }

    public static void addToMap(Map<String, String> dest, Map<?, ?> src) {
        for (Map.Entry<?, ?> entry : src.entrySet()) {
            dest.put(entry.getKey().toString(), entry.getValue().toString());
        }
    }

    public static Properties loadProperties(File propertyFile) {
        try {
            FileInputStream inputStream = new FileInputStream(propertyFile);
            try {
                return loadProperties(inputStream);
            } finally {
                inputStream.close();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Properties loadProperties(URL url) {
        try {
            URLConnection uc = url.openConnection();
            uc.setUseCaches(false);
            return loadProperties(uc.getInputStream());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Properties loadProperties(InputStream inputStream) {
        Properties properties = new Properties();
        try {
            properties.load(inputStream);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            IoActions.closeQuietly(inputStream);
        }
        return properties;
    }

    public static void saveProperties(Properties properties, File propertyFile) {
        try {
            FileOutputStream propertiesFileOutputStream = new FileOutputStream(propertyFile);
            try {
                properties.store(propertiesFileOutputStream, null);
            } finally {
                propertiesFileOutputStream.close();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void saveProperties(Properties properties, OutputStream outputStream) {
        try {
            try {
                properties.store(outputStream, null);
            } finally {
                outputStream.close();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Map<Object, Object> map(Object... objects) {
        Map<Object, Object> map = new HashMap<Object, Object>();
        assert objects.length % 2 == 0;
        for (int i = 0; i < objects.length; i += 2) {
            map.put(objects[i], objects[i + 1]);
        }
        return map;
    }

    public static String toString(Iterable<?> names) {
        Formatter formatter = new Formatter();
        boolean first = true;
        for (Object name : names) {
            if (first) {
                formatter.format("'%s'", name);
                first = false;
            } else {
                formatter.format(", '%s'", name);
            }
        }
        return formatter.toString();
    }

    /**
     * Converts an arbitrary string to a camel-case string which can be used in a Java identifier. Eg, with_underscores -&gt; withUnderscores
     */
    public static String toCamelCase(CharSequence string) {
        return toCamelCase(string, false);
    }

    public static String toLowerCamelCase(CharSequence string) {
        return toCamelCase(string, true);
    }

    private static String toCamelCase(CharSequence string, boolean lower) {
        if (string == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        Matcher matcher = WORD_SEPARATOR.matcher(string);
        int pos = 0;
        boolean first = true;
        while (matcher.find()) {
            String chunk = string.subSequence(pos, matcher.start()).toString();
            pos = matcher.end();
            if (chunk.isEmpty()) {
                continue;
            }
            if (lower && first) {
                chunk = StringUtils.uncapitalize(chunk);
                first = false;
            } else {
                chunk = StringUtils.capitalize(chunk);
            }
            builder.append(chunk);
        }
        String rest = string.subSequence(pos, string.length()).toString();
        if (lower && first) {
            rest = StringUtils.uncapitalize(rest);
        } else {
            rest = StringUtils.capitalize(rest);
        }
        builder.append(rest);
        return builder.toString();
    }

    /**
     * Converts an arbitrary string to upper case identifier with words separated by _. Eg, camelCase -&gt; CAMEL_CASE
     */
    public static String toConstant(CharSequence string) {
        if (string == null) {
            return null;
        }
        return toWords(string, '_').toUpperCase(Locale.ROOT);
    }

    /**
     * Converts an arbitrary string to space-separated words. Eg, camelCase -&gt; camel case, with_underscores -&gt; with underscores
     */
    public static String toWords(CharSequence string) {
        return toWords(string, ' ');
    }

    public static String toWords(CharSequence string, char separator) {
        if (string == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        int pos = 0;
        Matcher matcher = UPPER_LOWER.matcher(string);
        while (pos < string.length()) {
            matcher.find(pos);
            if (matcher.end() == pos) {
                // Not looking at a match
                pos++;
                continue;
            }
            if (builder.length() > 0) {
                builder.append(separator);
            }
            String group1 = matcher.group(1).toLowerCase(Locale.ROOT);
            String group2 = matcher.group(2);
            if (group2.length() == 0) {
                builder.append(group1);
            } else {
                if (group1.length() > 1) {
                    builder.append(group1.substring(0, group1.length() - 1));
                    builder.append(separator);
                    builder.append(group1.substring(group1.length() - 1));
                } else {
                    builder.append(group1);
                }
                builder.append(group2);
            }
            pos = matcher.end();
        }

        return builder.toString();
    }

    public static byte[] serialize(Object object) {
        StreamByteBuffer buffer = new StreamByteBuffer();
        serialize(object, buffer.getOutputStream());
        return buffer.readAsByteArray();
    }

    public static void serialize(Object object, OutputStream outputStream) {
        try {
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream);
            objectOutputStream.writeObject(object);
            objectOutputStream.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static <T> Comparator<T> last(final Comparator<? super T> comparator, final T lastValue) {
        return new Comparator<T>() {
            @Override
            public int compare(T o1, T o2) {
                boolean o1Last = comparator.compare(o1, lastValue) == 0;
                boolean o2Last = comparator.compare(o2, lastValue) == 0;
                if (o1Last && o2Last) {
                    return 0;
                }
                if (o1Last && !o2Last) {
                    return 1;
                }
                if (!o1Last && o2Last) {
                    return -1;
                }
                return comparator.compare(o1, o2);
            }
        };
    }

    /**
     * Calls the given callable converting any thrown exception to an unchecked exception via {@link UncheckedException#throwAsUncheckedException(Throwable)}
     *
     * @param callable The callable to call
     * @param <T> Callable's return type
     * @return The value returned by {@link Callable#call()}
     */
    @Nullable
    public static <T> T uncheckedCall(Callable<T> callable) {
        try {
            return callable.call();
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    public static <T extends Enum<T>> T toEnum(Class<? extends T> enumType, Object value) {
        if (enumType.isInstance(value)) {
            return enumType.cast(value);
        }
        if (value instanceof CharSequence) {
            final String literal = value.toString();
            T match = findEnumValue(enumType, literal);
            if (match != null) {
                return match;
            }

            final String alternativeLiteral = toWords(literal, '_');
            match = findEnumValue(enumType, alternativeLiteral);
            if (match != null) {
                return match;
            }

            throw new IllegalArgumentException(
                String.format("Cannot convert string value '%s' to an enum value of type '%s' (valid case insensitive values: %s)",
                    literal, enumType.getName(), CollectionUtils.join(", ", CollectionUtils.collect(Arrays.asList(enumType.getEnumConstants()), new InternalTransformer<Object, T>() {
                        @Override
                        public String transform(T t) {
                            return t.name();
                        }
                    }))
                )
            );
        }
        throw new IllegalArgumentException(String.format("Cannot convert value '%s' of type '%s' to enum type '%s'",
            value, value.getClass().getName(), enumType.getName()));
    }

    @Nullable
    private static <T extends Enum<T>> T findEnumValue(Class<? extends T> enumType, final String literal) {
        List<? extends T> enumConstants = Arrays.asList(enumType.getEnumConstants());
        return CollectionUtils.findFirst(enumConstants, new Spec<T>() {
            @Override
            public boolean isSatisfiedBy(T enumValue) {
                return enumValue.name().equalsIgnoreCase(literal);
            }
        });
    }

    public static <T extends Enum<T>> EnumSet<T> toEnumSet(Class<T> enumType, Object[] values) {
        return toEnumSet(enumType, Arrays.asList(values));
    }

    public static <T extends Enum<T>> EnumSet<T> toEnumSet(Class<T> enumType, Iterable<?> values) {
        EnumSet<T> result = EnumSet.noneOf(enumType);
        for (Object value : values) {
            result.add(toEnum(enumType, value));
        }
        return result;
    }

    /**
     * Checks whether the fist {@link CharSequence} ends with the second.
     *
     * If the {@link CharSequence#charAt(int)} method of both sequences is fast,
     * this check is faster than converting them to Strings and using {@link String#endsWith(String)}.
     */
    public static boolean endsWith(CharSequence longer, CharSequence shorter) {
        if (longer instanceof String && shorter instanceof String) {
            return ((String) longer).endsWith((String) shorter);
        }
        int longerLength = longer.length();
        int shorterLength = shorter.length();
        if (longerLength < shorterLength) {
            return false;
        }
        for (int i = shorterLength; i > 0; i--) {
            if (longer.charAt(longerLength - i) != shorter.charAt(shorterLength - i)) {
                return false;
            }
        }
        return true;
    }

    public static URI toSecureUrl(URI scriptUri) {
        try {
            return new URI("https", null, scriptUri.getHost(), scriptUri.getPort(), scriptUri.getPath(), scriptUri.getQuery(), scriptUri.getFragment());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("could not make url use https", e);
        }
    }

    public static boolean isSecureUrl(URI url) {
        /*
         * TL;DR: http://127.0.0.1 will bypass this validation, http://localhost will fail this validation.
         *
         * Hundreds of Gradle's integration tests use a local web-server to test logic that relies upon
         * this behavior.
         *
         * Changing all of those tests so that they use a KeyStore would have been impractical.
         * Instead, the test fixture was updated to use 127.0.0.1 when making HTTP requests.
         *
         * This allows tests that still want to exercise the deprecation logic to use http://localhost
         * which will bypass this check and trigger the validation.
         *
         * It's important to note that the only way to create a certificate for an IP address is to bind
         * the IP address as a 'Subject Alternative Name' which was deemed far too complicated for our test
         * use case.
         *
         * Additionally, in the rare case that a user or a plugin author truly needs to test with a localhost
         * server, they can use http://127.0.0.1
         */
        if ("127.0.0.1".equals(url.getHost())) {
            return true;
        }

        final String scheme = url.getScheme();
        return !"http".equalsIgnoreCase(scheme);
    }

}
