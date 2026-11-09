/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.tooling.events.problems.internal;

import org.gradle.tooling.events.problems.Frame;
import org.gradle.tooling.events.problems.Locality;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class DefaultFrame implements Frame {

    private final String displayName;
    private final Locality locality;

    public DefaultFrame(String displayName, Locality locality) {
        this.displayName = displayName;
        this.locality = locality;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public Locality getLocality() {
        return locality;
    }
}
