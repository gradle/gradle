/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.artifacts.component;

import com.google.common.base.Objects;
import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import org.gradle.api.artifacts.component.BuildIdentifier;

public class DefaultBuildIdentifier implements BuildIdentifier {
    private static final Interner<DefaultBuildIdentifier> CURRENT_INSTANCES_INTERNER = Interners.newWeakInterner();
    private static final Interner<DefaultBuildIdentifier> OTHER_INSTANCES_INTERNER = Interners.newStrongInterner();
    private final String name;
    private final boolean current;
    private String displayName;

    private DefaultBuildIdentifier(String name, boolean current) {
        this.name = name;
        this.current = current;
    }

    public static DefaultBuildIdentifier of(String name, boolean current) {
        DefaultBuildIdentifier instance = new DefaultBuildIdentifier(name, current);
        // instance contains state in the "current" field which isn't part of equals/hashCode
        // see DefaultProjectComponentIdentifier for similar workaround
        if (current) {
            return CURRENT_INSTANCES_INTERNER.intern(instance);
        } else {
            return OTHER_INSTANCES_INTERNER.intern(instance);
        }
    }

    public static DefaultBuildIdentifier of(String name) {
        return of(name, false);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isCurrentBuild() {
        return current;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DefaultBuildIdentifier)) {
            return false;
        }
        DefaultBuildIdentifier that = (DefaultBuildIdentifier) o;

        // "current" field isn't included in equals or hashCode
        // DefaultIncludedBuildExecuter.waitForExistingBuildToComplete assumes this

        return Objects.equal(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name);
    }

    @Override
    public String toString() {
        if (displayName == null) {
            displayName = createDisplayName();
        }
        return displayName;
    }

    protected String createDisplayName() {
        return "build '" + name + "'";
    }
}
