/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.vcs.internal;

import org.gradle.internal.Cast;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.vcs.VersionControlSpec;
import org.gradle.vcs.git.GitVersionControlSpec;
import org.gradle.vcs.git.internal.DefaultGitVersionControlSpec;

import javax.annotation.Nullable;

public class VersionControlSpecFactory {
    private final Instantiator instantiator;

    public VersionControlSpecFactory(Instantiator instantiator) {
        this.instantiator = instantiator;
    }

    @Nullable
    public <T extends VersionControlSpec> T create(Class<T> specType) {
        if (GitVersionControlSpec.class.isAssignableFrom(specType)) {
            return Cast.uncheckedCast(instantiator.newInstance(DefaultGitVersionControlSpec.class));
        } else if (DirectoryRepositorySpec.class.isAssignableFrom(specType)) {
            return Cast.uncheckedCast(instantiator.newInstance(DirectoryRepositorySpec.class));
        }
        return null;
    }
}
