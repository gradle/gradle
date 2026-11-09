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

import org.gradle.tooling.events.problems.Locality;
import org.gradle.tooling.events.problems.ProjectFrame;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class DefaultProjectFrame extends DefaultFrame implements ProjectFrame {

    private final String buildTreePath;

    public DefaultProjectFrame(String displayName, Locality locality, String buildTreePath) {
        super(displayName, locality);
        this.buildTreePath = buildTreePath;
    }

    @Override
    public String getBuildTreePath() {
        return buildTreePath;
    }
}
