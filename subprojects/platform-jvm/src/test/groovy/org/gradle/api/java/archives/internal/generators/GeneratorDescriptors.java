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

package org.gradle.api.java.archives.internal.generators;

import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.generator.Size;
import com.pholser.junit.quickcheck.internal.generator.NullAllowed;
import org.gradle.api.java.archives.internal.DefaultManifest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.HashMap;
import java.util.Map;

public class GeneratorDescriptors {
    @From(value = KnownManifestKeyGenerator.class, frequency = 10)
    @Regex("[-_a-zA-Z0-9]+")
    @Size(min = 1, max = 70)
    @From(value = RegexStringGenerator.class, frequency = 90)
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE_USE)
    public @interface ManifestKey {
    }

    @Regex("([a-zA-Z0-9 -\t:@_]|नि|😃|丈)+")
    @Size(min = 0, max = 200)
    @From(value = RegexStringGenerator.class)
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE_USE)
    public @interface ManifestValue {
    }

    public final @Size(min = 0, max = 10) HashMap<@ManifestKey String, @ManifestValue String> manifestAttributes = null;

    // Generic signatures are not yet supported by @From(Fields.class)
    // See https://github.com/pholser/junit-quickcheck/issues/239
    // See https://github.com/pholser/junit-quickcheck/issues/240
    // As of now, we "bake" all the generator configuration to this class.
    public static class ManifestBuilder {
        @FromField(value = GeneratorDescriptors.class, field = "manifestAttributes")
        public HashMap<String, String> mainAttributes;

        @Size(min=0, max=5)
        public HashMap<@ManifestKey @NullAllowed(probability = 0.1f) String,
            @FromField(value = GeneratorDescriptors.class, field = "manifestAttributes")
            HashMap<String, String>> sections;

        public DefaultManifest build() {
            DefaultManifest m = new DefaultManifest(null);
            m.attributes(mainAttributes);
            for (Map.Entry<String, HashMap<String, String>> sections : sections.entrySet()) {
                m.attributes(sections.getValue(), sections.getKey());
            }
            return m;
        }

        @Override
        public String toString() {
            return "ManifestBuilder{" +
                "mainAttributes=" + mainAttributes +
                ", sections=" + sections +
                '}';
        }
    }
}
