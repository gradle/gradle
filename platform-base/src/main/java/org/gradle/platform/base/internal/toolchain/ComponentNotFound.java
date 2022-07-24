/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.platform.base.internal.toolchain;

import org.gradle.internal.logging.text.DiagnosticsVisitor;

import java.util.Collection;
import java.util.Collections;

public class ComponentNotFound<T> implements SearchResult<T> {
    private final String message;
    private final Collection<String> locations;

    public ComponentNotFound(String message) {
        this.message = message;
        this.locations = Collections.emptyList();
    }

    public ComponentNotFound(String message, Collection<String> locations) {
        this.message = message;
        this.locations = locations;
    }

    @Override
    public T getComponent() {
        return null;
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public void explain(DiagnosticsVisitor visitor) {
        visitor.node(message);
        if (!locations.isEmpty()) {
            visitor.startChildren();
            for (String location : locations) {
                visitor.node(location);
            }
            visitor.endChildren();
        }
    }
}
