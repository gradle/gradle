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

import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class KnownManifestKeyGenerator extends Generator<String> {
    private final List<String> keys = new ArrayList<String>(
        Arrays.asList(
            "Manifest-Version",
            "Implementation-Version",
            "implementation-Title",
            "Bundle-License",
            "Class-Path",
            "Main-Class",
            "0123456789012345678901234567890123456789012345678901234567890123456789"
        )
    );

    private final StringBuilder sb = new StringBuilder();

    public KnownManifestKeyGenerator() {
        super(String.class);
    }

    @Override
    public String generate(SourceOfRandomness random, GenerationStatus status) {
        String key = keys.get(random.nextInt(keys.size()));
        int upd = random.nextInt(3);
        if (upd == 0) {
            return key;
        }

        // Update casing of several letters
        sb.setLength(0);
        sb.append(key);
        for (int i = 0; i < upd; i++) {
            int idx = random.nextInt(key.length());
            char c = sb.charAt(idx);
            if (Character.isLowerCase(c)) {
                c = Character.toUpperCase(c);
            } else {
                c = Character.toLowerCase(c);
            }
            sb.setCharAt(idx, c);
        }

        return sb.toString();
    }
}
