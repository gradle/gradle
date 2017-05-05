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

package org.gradle.internal;

import org.gradle.api.Describable;

public class Describables {
    private Describables() {}

    /**
     * Returns a describable that calls {@link Object#toString()} on the provided value each time the display name is queried.
     */
    public static Describable of(Object displayName) {
        return new FixedDescribable(displayName);
    }

    /**
     * Returns a describable that calls {@link Object#toString()} on the provided values each time the display name is queried, separating the values with a space character.
     */
    public static Describable of(Object part1, Object part2) {
        return new TwoPartDescribable(part1, part2);
    }

    /**
     * Returns a describable that calls {@link Object#toString()} on the provided values each time the display name is queried, separating the values with a space character.
     */
    public static Describable of(Object part1, Object part2, Object part3) {
        return new ThreePartDescribable(part1, part2, part3);
    }

    /**
     * Returns a describable that calls {@link Object#toString()} on the provided values each time the display name is queried, separating the values with a space character.
     */
    public static Describable of(Describable part1, String part2, String part3) {
        return new ThreePartWithPrefixDescribable(part1, part2, part3);
    }

    private static abstract class AbstractDescribable implements Describable {
        @Override
        public String toString() {
            return getDisplayName();
        }
    }

    private static class FixedDescribable extends AbstractDescribable {
        private final Object displayName;

        FixedDescribable(Object displayName) {
            this.displayName = displayName;
        }

        @Override
        public String getDisplayName() {
            return displayName.toString();
        }
    }

    private static class TwoPartDescribable extends AbstractDescribable {
        private final Object part1;
        private final Object part2;

        TwoPartDescribable(Object part1, Object part2) {
            this.part1 = part1;
            this.part2 = part2;
        }

        @Override
        public String getDisplayName() {
            return part1.toString() + ' ' + part2.toString();
        }
    }

    private static class ThreePartDescribable extends AbstractDescribable {
        private final Object part1;
        private final Object part2;
        private final Object part3;

        ThreePartDescribable(Object part1, Object part2, Object part3) {
            this.part1 = part1;
            this.part2 = part2;
            this.part3 = part3;
        }

        @Override
        public String getDisplayName() {
            return part1.toString() + ' ' + part2.toString() + ' ' + part3.toString();
        }
    }

    private static class ThreePartWithPrefixDescribable extends AbstractDescribable {
        private final Describable part1;
        private final String part2;
        private final String part3;

        ThreePartWithPrefixDescribable(Describable part1, String part2, String part3) {
            this.part1 = part1;
            this.part2 = part2;
            this.part3 = part3;
        }

        @Override
        public String getDisplayName() {
            return part1.getDisplayName() + ' ' + part2 + ' ' + part3;
        }
    }
}
