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

package org.gradle.internal.snapshot.impl;

import org.gradle.api.Named;
import org.gradle.api.internal.model.NamedObjectInstantiator;
import org.gradle.internal.Cast;
import org.jspecify.annotations.Nullable;

public class CoercingStringValueSnapshot extends StringValueSnapshot {
    private final NamedObjectInstantiator instantiator;

    public CoercingStringValueSnapshot(String value, NamedObjectInstantiator instantiator) {
        super(value);
        this.instantiator = instantiator;
    }

    @Nullable
    @Override
    public <S> S coerce(Class<S> type) {
        if (type.isInstance(getValue())) {
            return type.cast(this);
        }
        if (type.isEnum()) {
            // TODO: Remove support for raw Enums in Gradle 10.0.0
            // Once every attribute value implements Named, this branch and findEnumConstant below
            // both go away: an enum type implementing Named is served by the Named branch.
            return type.cast(findEnumConstant(Cast.uncheckedNonnullCast(type.asSubclass(Enum.class)), getValue()));
        }
        if (Named.class.isAssignableFrom(type)) {
            return type.cast(instantiator.named(type.asSubclass(Named.class), getValue()));
        }
        if (Integer.class.equals(type)) {
            return type.cast(Integer.parseInt(getValue()));
        }
        return null;
    }

    /**
     * Resolves an enum constant from the String form a value of that enum type is written as.
     * <p>
     * A value is written as {@link Named#getName()} when its type implements {@link Named} and as
     * {@link Enum#name()} otherwise. An enum type can be both, so a match on {@code getName()} is
     * attempted first: {@link Enum#valueOf} alone cannot read back an enum whose {@code getName()}
     * differs from its {@code name()}.
     */
    private static <S extends Enum<S>> S findEnumConstant(Class<S> enumType, String value) {
        if (Named.class.isAssignableFrom(enumType)) {
            for (S constant : enumType.getEnumConstants()) {
                if (((Named) constant).getName().equals(value)) {
                    return constant;
                }
            }
        }
        // Also covers a Named enum whose getName() does return name(), and reports the unknown
        // value the same way as before for anything that matches no constant at all.
        return Enum.valueOf(enumType, value);
    }
}
