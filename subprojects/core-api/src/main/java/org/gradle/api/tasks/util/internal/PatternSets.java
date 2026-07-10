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

public class PatternSets {
    private static final PatternSetFactory PATTERN_SET_FACTORY = new DefaultPatternSetFactory(PatternSpecFactory.INSTANCE);

    /**
     * Should use as an injected service instead.
     *
     * @deprecated Should use as an injected service instead.
     */
    @Deprecated
    public static PatternSetFactory getNonCachingPatternSetFactory() {
        return PATTERN_SET_FACTORY;
    }
}
