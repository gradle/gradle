/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.tooling.internal.provider.serialization;

import org.gradle.internal.classloader.CachingClassLoader;
import org.gradle.internal.classloader.ClassLoaderSpec;
import org.gradle.internal.classloader.FilteringClassLoader;
import org.gradle.internal.classloader.MultiParentClassLoader;
import org.gradle.internal.classloader.SystemClassLoaderSpec;
import org.gradle.internal.classloader.VisitableURLClassLoader;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;
import org.jspecify.annotations.NullMarked;

import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@NullMarked
public class ClassLoaderSpecSerializer implements Serializer<ClassLoaderSpec> {
    private static final byte SYSTEM = 0;
    private static final byte CACHING = 1;
    private static final byte MULTI_PARENT = 2;
    private static final byte FILTERING = 3;
    private static final byte VISITABLE_URL = 4;
    private static final byte CLIENT_OWNED = 5;
    private static final byte KNOWN = 6;

    @Override
    public void write(Encoder encoder, ClassLoaderSpec spec) throws Exception {
        Class<? extends ClassLoaderSpec> type = spec.getClass();
        if (type == SystemClassLoaderSpec.class) {
            encoder.writeByte(SYSTEM);
        } else if (type == CachingClassLoader.Spec.class) {
            encoder.writeByte(CACHING);
        } else if (type == MultiParentClassLoader.Spec.class) {
            encoder.writeByte(MULTI_PARENT);
        } else if (type == FilteringClassLoader.Spec.class) {
            encoder.writeByte(FILTERING);
            writeFiltering(encoder, (FilteringClassLoader.Spec) spec);
        } else if (type == VisitableURLClassLoader.Spec.class) {
            encoder.writeByte(VISITABLE_URL);
            writeVisitableUrl(encoder, (VisitableURLClassLoader.Spec) spec);
        } else if (type == ClientOwnedClassLoaderSpec.class) {
            encoder.writeByte(CLIENT_OWNED);
            writeClientOwned(encoder, (ClientOwnedClassLoaderSpec) spec);
        } else if (WellKnownClassLoaderRegistry.isKnownClassLoaderSpec(spec)) {
            encoder.writeByte(KNOWN);
            WellKnownClassLoaderRegistry.writeKnownClassLoaderSpec(encoder, spec);
        } else {
            throw new IllegalArgumentException("Cannot serialize ClassLoaderSpec of type " + type.getName());
        }
    }

    @Override
    public ClassLoaderSpec read(Decoder decoder) throws Exception {
        byte tag = decoder.readByte();
        switch (tag) {
            case SYSTEM:
                return SystemClassLoaderSpec.INSTANCE;
            case CACHING:
                return new CachingClassLoader.Spec();
            case MULTI_PARENT:
                return new MultiParentClassLoader.Spec();
            case FILTERING:
                return readFiltering(decoder);
            case VISITABLE_URL:
                return readVisitableUrl(decoder);
            case CLIENT_OWNED:
                return readClientOwned(decoder);
            case KNOWN:
                return WellKnownClassLoaderRegistry.readKnownClassLoaderSpec(decoder);
            default:
                throw new IllegalArgumentException("Unexpected ClassLoaderSpec tag " + tag);
        }
    }

    private void writeFiltering(Encoder encoder, FilteringClassLoader.Spec spec) throws Exception {
        writeStrings(encoder, spec.getClassNames());
        writeStrings(encoder, spec.getDisallowedClassNames());
        writeStrings(encoder, spec.getPackagePrefixes());
        writeStrings(encoder, spec.getDisallowedPackagePrefixes());
        writeStrings(encoder, spec.getPackageNames());
        writeStrings(encoder, spec.getResourceNames());
        writeStrings(encoder, spec.getResourcePrefixes());
    }

    private FilteringClassLoader.Spec readFiltering(Decoder decoder) throws Exception {
        List<String> classNames = readStrings(decoder);
        List<String> disallowedClassNames = readStrings(decoder);
        List<String> packagePrefixes = readStrings(decoder);
        List<String> disallowedPackagePrefixes = readStrings(decoder);
        List<String> packageNames = readStrings(decoder);
        List<String> resourceNames = readStrings(decoder);
        List<String> resourcePrefixes = readStrings(decoder);
        return new FilteringClassLoader.Spec(classNames, packageNames, packagePrefixes, resourcePrefixes, resourceNames, disallowedClassNames, disallowedPackagePrefixes);
    }

    private void writeVisitableUrl(Encoder encoder, VisitableURLClassLoader.Spec spec) throws Exception {
        encoder.writeNullableString(spec.getName());
        encoder.writeSmallInt(spec.getClasspath().size());
        for (URL url : spec.getClasspath()) {
            encoder.writeString(url.toExternalForm());
        }
    }

    private VisitableURLClassLoader.Spec readVisitableUrl(Decoder decoder) throws Exception {
        String name = decoder.readNullableString();
        int size = decoder.readSmallInt();
        List<URL> classpath = new ArrayList<URL>(size);
        for (int i = 0; i < size; i++) {
            // Deliberately new URL(String): it accepts URLs that are not strictly valid URIs (e.g. file URLs with
            // spaces), matching how the classpath URLs were produced. The value is not resolved here.
            @SuppressWarnings("deprecation")
            URL url = new URL(decoder.readString());
            classpath.add(url);
        }
        return new VisitableURLClassLoader.Spec(name, classpath);
    }

    private void writeClientOwned(Encoder encoder, ClientOwnedClassLoaderSpec spec) throws Exception {
        encoder.writeSmallInt(spec.getClasspath().size());
        for (URI uri : spec.getClasspath()) {
            encoder.writeString(uri.toString());
        }
    }

    private ClientOwnedClassLoaderSpec readClientOwned(Decoder decoder) throws Exception {
        int size = decoder.readSmallInt();
        List<URI> classpath = new ArrayList<URI>(size);
        for (int i = 0; i < size; i++) {
            classpath.add(new URI(decoder.readString()));
        }
        return new ClientOwnedClassLoaderSpec(classpath);
    }

    private void writeStrings(Encoder encoder, Collection<String> strings) throws Exception {
        encoder.writeSmallInt(strings.size());
        for (String string : strings) {
            encoder.writeString(string);
        }
    }

    private List<String> readStrings(Decoder decoder) throws Exception {
        int size = decoder.readSmallInt();
        List<String> strings = new ArrayList<String>(size);
        for (int i = 0; i < size; i++) {
            strings.add(decoder.readString());
        }
        return strings;
    }
}
