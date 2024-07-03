/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.build;

import org.gradle.api.NonNullApi;
import org.gradle.api.internal.GradleInternal;

@NonNullApi
public class DefaultDeferredRootBuildGradle implements DeferredRootBuildGradle {

    private GradleInternal rootBuildGradle;

    @Override
    public void attach(GradleInternal rootBuildGradle) {
        if (this.rootBuildGradle != null) {
            throw new IllegalStateException("Cannot re-attach root build Gradle");
        }

        this.rootBuildGradle = rootBuildGradle;
    }

    @Override
    public GradleInternal getGradle() {
        if (rootBuildGradle == null) {
            throw new IllegalStateException("Root build Gradle is not attached");
        }

        return rootBuildGradle;
    }
}
