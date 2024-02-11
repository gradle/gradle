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

package org.gradle.internal.component.external.model;

import javax.annotation.Nullable;

public class AdditionalVariant {
    private final String name;
    private final String base;
    private final boolean lenient;

    public AdditionalVariant(String name) {
        this(name, null, false);
    }

    public AdditionalVariant(String name, @Nullable String base, boolean lenient) {
        this.name = name;
        this.base = base;
        this.lenient = lenient;
    }

    public String getName() {
        return name;
    }

    public String getBase() {
        return base;
    }

    public boolean isLenient() {
        return lenient;
    }
}
