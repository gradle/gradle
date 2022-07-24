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

import com.google.common.base.Objects;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.Describable;

public class Describables {
    private Describables() {
    }

    /**
     * Returns a describable that converts the provided value to a string each time the display name is queried. Can pass a {@link Describable} or {@link DisplayName}.
     */
    public static DisplayName of(Object displayName) {
        if (displayName instanceof DisplayName) {
            return (DisplayName) displayName;
        }
        return new FixedDescribable(displayName);
    }

    /**
     * Returns a describable that converts the provided values to string each time the display name is queried, separating the values with a space character. Can pass a {@link Describable} or {@link DisplayName} for any parameter.
     */
    public static DisplayName of(Object part1, Object part2) {
        return new TwoPartDescribable(part1, part2);
    }

    /**
     * Returns a describable that converts the provided values to string each time the display name is queried, separating the values with a space character. Can pass a {@link Describable} or {@link DisplayName} for any parameter.
     */
    public static DisplayName of(Object part1, Object part2, Object part3) {
        return new ThreePartDescribable(part1, part2, part3);
    }

    /**
     * Returns a describable for a description and quoted value.
     */
    public static DisplayName quoted(final Object description, final Object value) {
        return new AbstractDescribable() {
            @Override
            public String getCapitalizedDisplayName() {
                StringBuilder builder = new StringBuilder(64);
                appendCapDisplayName(description, builder);
                builder.append(" '");
                appendDisplayName(value, builder);
                builder.append('\'');
                return builder.toString();
            }

            @Override
            public String getDisplayName() {
                StringBuilder builder = new StringBuilder(64);
                appendDisplayName(description, builder);
                builder.append(" '");
                appendDisplayName(value, builder);
                builder.append('\'');
                return builder.toString();
            }
        };
    }

    /**
     * Returns a describable for an object that has a type and name. Quotes are added around the name.
     */
    public static DisplayName withTypeAndName(final Object type, final String name) {
        return new AbstractDescribable() {
            @Override
            public String getCapitalizedDisplayName() {
                StringBuilder result = asMutable();
                result.setCharAt(0, Character.toUpperCase(result.charAt(0)));
                return result.toString();
            }

            @Override
            public String getDisplayName() {
                return asMutable().toString();
            }

            private StringBuilder asMutable() {
                String typeStr = type.toString();
                StringBuilder result = new StringBuilder(typeStr.length() + name.length() + 3);
                result.append(typeStr);
                result.append(" '");
                result.append(name);
                result.append('\'');
                return result;
            }
        };
    }

    /**
     * Returns a describable that calculates the display name of the given describable when first requested and reuses the result.
     */
    public static DisplayName memoize(Describable describable) {
        return new MemoizingDescribable(describable);
    }

    private static void appendDisplayName(Object src, StringBuilder stringBuilder) {
        if (src instanceof Describable) {
            Describable describable = (Describable) src;
            stringBuilder.append(describable.getDisplayName());
        } else {
            stringBuilder.append(src.toString());
        }
    }

    private static void appendCapDisplayName(Object src, StringBuilder stringBuilder) {
        if (src instanceof DisplayName) {
            DisplayName displayName = (DisplayName) src;
            stringBuilder.append(displayName.getCapitalizedDisplayName());
        } else {
            int pos = stringBuilder.length();
            if (src instanceof Describable) {
                Describable describable = (Describable) src;
                stringBuilder.append(describable.getDisplayName());
            } else {
                stringBuilder.append(src.toString());
            }
            stringBuilder.setCharAt(pos, Character.toUpperCase(stringBuilder.charAt(pos)));
        }
    }

    private static abstract class AbstractDescribable implements DisplayName {
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
            if (displayName instanceof CharSequence) {
                return displayName.toString();
            }
            StringBuilder builder = new StringBuilder(32);
            appendDisplayName(displayName, builder);
            return builder.toString();
        }

        @Override
        public String getCapitalizedDisplayName() {
            StringBuilder builder = new StringBuilder();
            appendCapDisplayName(displayName, builder);
            return builder.toString();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            FixedDescribable that = (FixedDescribable) o;
            return Objects.equal(displayName, that.displayName);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(displayName);
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
            StringBuilder builder = new StringBuilder(48);
            appendDisplayName(part1, builder);
            builder.append(' ');
            appendDisplayName(part2, builder);
            return builder.toString();
        }

        @Override
        public String getCapitalizedDisplayName() {
            StringBuilder builder = new StringBuilder(48);
            appendCapDisplayName(part1, builder);
            builder.append(' ');
            appendDisplayName(part2, builder);
            return builder.toString();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            TwoPartDescribable that = (TwoPartDescribable) o;
            return Objects.equal(part1, that.part1) &&
                Objects.equal(part2, that.part2);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(part1, part2);
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
            StringBuilder builder = new StringBuilder(64);
            appendDisplayName(part1, builder);
            builder.append(' ');
            appendDisplayName(part2, builder);
            builder.append(' ');
            appendDisplayName(part3, builder);
            return builder.toString();
        }

        @Override
        public String getCapitalizedDisplayName() {
            StringBuilder builder = new StringBuilder(64);
            appendCapDisplayName(part1, builder);
            builder.append(' ');
            appendDisplayName(part2, builder);
            builder.append(' ');
            appendDisplayName(part3, builder);
            return builder.toString();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ThreePartDescribable that = (ThreePartDescribable) o;
            return Objects.equal(part1, that.part1) &&
                Objects.equal(part2, that.part2) &&
                Objects.equal(part3, that.part3);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(part1, part2, part3);
        }
    }

    private static class MemoizingDescribable extends AbstractDescribable {
        private Describable describable;
        private String displayName;
        private String capDisplayName;

        MemoizingDescribable(Describable describable) {
            this.describable = describable;
        }

        @Override
        public String getCapitalizedDisplayName() {
            synchronized (this) {
                if (capDisplayName == null) {
                    capDisplayName = describable instanceof DisplayName ? ((DisplayName) describable).getCapitalizedDisplayName() : StringUtils.capitalize(getDisplayName());
                    if (displayName != null) {
                        describable = null;
                    }
                }
                return capDisplayName;
            }
        }

        @Override
        public String getDisplayName() {
            synchronized (this) {
                if (displayName == null) {
                    displayName = describable.getDisplayName();
                    if (capDisplayName != null || !(describable instanceof DisplayName)) {
                        describable = null;
                    }
                }
                return displayName;
            }
        }
    }
}
