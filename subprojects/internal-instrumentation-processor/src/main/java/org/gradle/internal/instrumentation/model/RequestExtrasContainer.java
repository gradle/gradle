/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.instrumentation.model;

import org.gradle.internal.Cast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class RequestExtrasContainer {
    private final List<RequestExtra> extras = new ArrayList<>();

    public List<RequestExtra> getAll() {
        return Collections.unmodifiableList(extras);
    }

    public <T> Optional<T> getByType(Class<T> type) {
        return Cast.uncheckedCast(extras.stream().filter(type::isInstance).findFirst());
    }

    public void add(RequestExtra extra) {
        extras.add(extra);
    }
}
