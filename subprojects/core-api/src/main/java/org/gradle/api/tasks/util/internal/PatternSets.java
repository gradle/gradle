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

package org.gradle.api.tasks.util.internal;

import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.Factory;

public class PatternSets {
    private static final Factory<PatternSet> PATTERN_SET_FACTORY = new PatternSetFactory(PatternSpecFactory.INSTANCE);

    /**
     * Should use as an injected service instead.
     * @deprecated Should use as an injected service instead.
     */
    @Deprecated
    public static Factory<PatternSet> getNonCachingPatternSetFactory() {
        return PATTERN_SET_FACTORY;
    }

    public static Factory<PatternSet> getPatternSetFactory(PatternSpecFactory patternSpecFactory) {
        return new PatternSetFactory(patternSpecFactory);
    }

    private static final class PatternSetFactory implements Factory<PatternSet> {
        private final PatternSpecFactory patternSpecFactory;

        private PatternSetFactory(PatternSpecFactory patternSpecFactory) {
            this.patternSpecFactory = patternSpecFactory;
        }

        @Override
        public PatternSet create() {
            return new InternalPatternSet(patternSpecFactory);
        }
    }

    // This is only required to avoid adding a new public constructor to the public `PatternSet` type.
    private static class InternalPatternSet extends PatternSet {
        public InternalPatternSet(PatternSpecFactory patternSpecFactory) {
            super(patternSpecFactory);
        }
    }

}
