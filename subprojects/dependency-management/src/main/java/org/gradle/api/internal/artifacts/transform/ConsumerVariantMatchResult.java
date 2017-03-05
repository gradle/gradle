/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.artifacts.transform;

import org.gradle.api.Transformer;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ConsumerVariantMatchResult {
    private int minDepth;
    private final List<ConsumerVariant> matches = new ArrayList<ConsumerVariant>();

    public void applyTo(ConsumerVariantMatchResult result) {
        result.matches.addAll(this.matches);
    }

    public void matched(ImmutableAttributes output, Transformer<List<File>, File> transform, int depth) {
        // Collect only the shortest paths
        if (minDepth == 0) {
            minDepth = depth;
        } else if (depth < minDepth) {
            matches.clear();
            minDepth = depth;
        } else if (depth > minDepth) {
            return;
        }
        matches.add(new ConsumerVariant(output, transform, depth));
    }

    public boolean hasMatches() {
        return !matches.isEmpty();
    }

    public Collection<ConsumerVariant> getMatches() {
        return matches;
    }

    public static class ConsumerVariant {
        final AttributeContainerInternal attributes;
        final Transformer<List<File>, File> transformer;
        final int depth;

        public ConsumerVariant(AttributeContainerInternal attributes, Transformer<List<File>, File> transformer, int depth) {
            this.attributes = attributes;
            this.transformer = transformer;
            this.depth = depth;
        }
    }
}
