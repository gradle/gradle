/*
 * Copyright 2021 the original author or authors.
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

import org.gradle.StartParameter;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

@ServiceScope(Scopes.BuildTree.class)
public class BuildCommencedTimeProvider {
    private final long fixedTime;

    public BuildCommencedTimeProvider(StartParameter startParameter) {
        String offsetStr = startParameter.getSystemPropertiesArgs().get("org.gradle.internal.test.clockoffset");
        long offset = offsetStr != null ? Long.parseLong(offsetStr) : 0;
        fixedTime = offset + System.currentTimeMillis();
    }

    public long getCurrentTime() {
        return fixedTime;
    }
}
