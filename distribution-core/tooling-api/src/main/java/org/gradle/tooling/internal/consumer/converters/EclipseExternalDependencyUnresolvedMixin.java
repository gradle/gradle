/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.tooling.internal.consumer.converters;

import org.gradle.tooling.model.ComponentSelector;
import org.gradle.tooling.model.eclipse.EclipseExternalDependency;

/**
 * This is used for compatibility with clients <6.7
 */
public class EclipseExternalDependencyUnresolvedMixin {

    public EclipseExternalDependencyUnresolvedMixin(EclipseExternalDependency dependency) {
    }

    public boolean isResolved() {
        return true;
    }

    public ComponentSelector getAttemptedSelector() {
        return null;
    }
}
