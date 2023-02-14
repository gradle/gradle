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

package org.gradle.api.internal.dependencies;

import org.gradle.api.artifacts.MutableVersionConstraint;
import org.gradle.api.artifacts.VersionConstraint;

public class VersionConstraintCopier {

    private VersionConstraintCopier() {}

    public static void copyVersionConstraint(VersionConstraint from, MutableVersionConstraint into) {
        if (from.getBranch() != null) {
            into.setBranch(from.getBranch());
        }
        if (!from.getRequiredVersion().isEmpty()) {
            into.require(from.getRequiredVersion());
        }
        if (!from.getStrictVersion().isEmpty()) {
            into.strictly(from.getStrictVersion());
        }
        if (!from.getPreferredVersion().isEmpty()) {
            into.prefer(from.getPreferredVersion());
        }
        if (!from.getRejectedVersions().isEmpty()) {
            into.reject(from.getRejectedVersions().toArray(new String[0]));
        }
    }
}
