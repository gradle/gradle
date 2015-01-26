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

public class CompositeBuildAbility implements BinaryBuildAbility {
    private BinaryBuildAbility[] abilities;

    public CompositeBuildAbility(BinaryBuildAbility... abilities) {
        this.abilities = abilities;
    }

    @Override
    public boolean isBuildable() {
        for (BinaryBuildAbility ability : abilities) {
            if (!ability.isBuildable()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void explain(TreeVisitor<? super String> visitor) {
        for (BinaryBuildAbility ability : abilities) {
            if (!ability.isBuildable()) {
                ability.explain(visitor);
            }
        }
    }
}
