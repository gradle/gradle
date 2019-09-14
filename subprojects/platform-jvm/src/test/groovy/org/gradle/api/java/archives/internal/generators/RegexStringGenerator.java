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
import com.pholser.junit.quickcheck.generator.Size;
import com.pholser.junit.quickcheck.generator.java.lang.AbstractStringGenerator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

public class RegexStringGenerator extends AbstractStringGenerator {
    private RegexStrGenerator regexGen;
    private Size size;

    public void configure(Regex regex) {
        this.regexGen = new RegexStrGenerator(regex.value());
    }

    public void configure(Size size) {
        this.size = size;
    }

    @Override
    public String generate(SourceOfRandomness random, GenerationStatus status) {
        if (regexGen == null) {
            throw new IllegalArgumentException("@Regex was not specified for RegexStringGenerator");
        }

        if (size == null) {
            throw new IllegalArgumentException("@Size was not specified for RegexStringGenerator");
        }

        return regexGen.random(random, size.min(), Math.min(Math.max(status.size(), size.min()), size.max()));
    }

    @Override
    public List<String> doShrink(SourceOfRandomness random, String larger) {
        // Shrink replaces chars at random, and it might produce strings
        // that fail to match the original regexp.
        return super.doShrink(random, larger)
            .stream()
            .filter(regexGen::matches)
            .collect(Collectors.toList());
    }

    @Override
    public BigDecimal magnitude(Object value) {
        return super.magnitude(value);
    }

    @Override
    protected int nextCodePoint(SourceOfRandomness random) {
        return 0;
    }

    @Override
    protected boolean codePointInRange(int codePoint) {
        return true;
    }
}
