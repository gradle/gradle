/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.workers.internal;

import com.google.common.collect.Lists;
import org.gradle.internal.classloader.FilteringClassLoader;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;

import java.util.List;
import java.util.Set;

public class FilteringClassLoaderSpecSerializer implements Serializer<FilteringClassLoader.Spec> {
    @Override
    public void write(Encoder encoder, FilteringClassLoader.Spec spec) throws Exception {
        encodeStrings(encoder, spec.getClassNames());
        encodeStrings(encoder, spec.getDisallowedClassNames());
        encodeStrings(encoder, spec.getPackagePrefixes());
        encodeStrings(encoder, spec.getDisallowedPackagePrefixes());
        encodeStrings(encoder, spec.getPackageNames());
        encodeStrings(encoder, spec.getResourceNames());
        encodeStrings(encoder, spec.getResourcePrefixes());
    }

    private void encodeStrings(Encoder encoder, Set<String> strings) throws Exception {
        encoder.writeInt(strings.size());
        for (String string : strings) {
            encoder.writeString(string);
        }
    }

    @Override
    public FilteringClassLoader.Spec read(Decoder decoder) throws Exception {
        List<String> classNames = decodeStrings(decoder);
        List<String> disallowedClassNames = decodeStrings(decoder);
        List<String> packagePrefixes = decodeStrings(decoder);
        List<String> disallowedPackagePrefixes = decodeStrings(decoder);
        List<String> packageNames = decodeStrings(decoder);
        List<String> resourceNames = decodeStrings(decoder);
        List<String> resourcePrefixes = decodeStrings(decoder);

        return new FilteringClassLoader.Spec(classNames, packageNames, packagePrefixes, resourcePrefixes, resourceNames, disallowedClassNames, disallowedPackagePrefixes);
    }

    private List<String> decodeStrings(Decoder decoder) throws Exception {
        List<String> strings = Lists.newArrayList();
        int size = decoder.readInt();
        for (int i=0; i<size; i++) {
            strings.add(decoder.readString());
        }
        return strings;
    }
}
