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

package org.gradle.util.internal;

import org.apache.commons.lang.ObjectUtils;
import org.gradle.api.Nullable;
import org.gradle.util.Equalizer;

public class DefaultEqualizer implements Equalizer<Object> {
    private static final Equalizer<Object> ENUM_EQUALIZER = new EnumEqualizer();

    @Override
    public boolean equals(@Nullable Object obj1, @Nullable Object obj2) {
        return ObjectUtils.equals(obj1, obj2) || ENUM_EQUALIZER.equals(obj1, obj2);
    }

    private static class EnumEqualizer implements Equalizer<Object> {
        public boolean equals(Object obj1, Object obj2) {
            if (!(obj1 instanceof Enum) || !(obj2 instanceof Enum)) {
                return false;
            }

            Enum e1 = (Enum) obj1;
            Enum e2 = (Enum) obj2;

            // Check enum equality without checking loading ClassLoader.
            // There is a slight risk that two versions of the same enum class are compared,
            // (that's why classloaders are used in equality checks), but checking both name
            // and ordinal should make this very unlikely.
            return e1.getClass().getCanonicalName().equals(e2.getClass().getCanonicalName())
                && e1.ordinal() == e2.ordinal()
                && e1.name().equals(e2.name());
        }
    }
}
