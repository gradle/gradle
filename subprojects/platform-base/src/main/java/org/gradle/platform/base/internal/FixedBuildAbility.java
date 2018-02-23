/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.platform.base.internal;

import org.gradle.util.TreeVisitor;

public class FixedBuildAbility implements BinaryBuildAbility {
    private final boolean buildable;

    public FixedBuildAbility(boolean buildable) {
        this.buildable = buildable;
    }

    @Override
    public boolean isBuildable() {
        return buildable;
    }

    @Override
    public void explain(TreeVisitor<? super String> visitor) {
        if (!buildable) {
            visitor.node("Disabled by user");
        }
    }
}