/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.plugins.ide.internal.tooling.model;

import org.gradle.tooling.internal.gradle.GradleBuildIdentity;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.Serializable;
import java.util.Collection;

@NullMarked
public class DefaultResilientGradleBuild implements Serializable, GradleBuildIdentity {
    private final boolean failed;
    @Nullable
    private final Collection<String> failures;
    private final DefaultGradleBuild gradleBuild;

    public DefaultResilientGradleBuild(DefaultGradleBuild gradleBuild, @Nullable Collection<String> failures) {
        this.gradleBuild = gradleBuild;
        this.failed = failures != null && !failures.isEmpty();
        this.failures = failures;
    }

    public DefaultGradleBuild getGradleBuild() {
        return gradleBuild;
    }

    public boolean didItFail() {
        return failed;
    }

    @Nullable
    public Collection<String> getFailures() {
        return failures;
    }

    @Override
    public File getRootDir() {
        return gradleBuild.getRootDir();
    }
}
