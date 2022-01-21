/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.tasks.diagnostics.internal.configurations.model;

import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.attributes.IncubatingAttributesChecker;

import javax.annotation.Nullable;
import java.util.Optional;

public final class ReportAttribute {
    private final String name;
    @Nullable private final Object value;
    private final boolean isIncubating;

    private ReportAttribute(Attribute<Object> key, @Nullable Object value) {
        this.name = key.getName();
        this.value = value;
        this.isIncubating = IncubatingAttributesChecker.isIncubating(key, value);
    }

    public static ReportAttribute fromAttributeInContainer(Attribute<?> attribute, AttributeContainer container) {
        @SuppressWarnings("unchecked") Attribute<Object> key = (Attribute<Object>) attribute;
        Object value = container.getAttribute(key);
        return new ReportAttribute(key, value);
    }

    public static ReportAttribute fromUncontainedAttribute(Attribute<?> attribute) {
        @SuppressWarnings("unchecked") Attribute<Object> key = (Attribute<Object>) attribute;
        return new ReportAttribute(key, null);
    }

    public String getName() {
        return name;
    }

    public Optional<Object> getValue() {
        return Optional.ofNullable(value);
    }

    public boolean isIncubating() {
        return isIncubating;
    }
}
