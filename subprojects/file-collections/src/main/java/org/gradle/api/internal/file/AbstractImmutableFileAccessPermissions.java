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

package org.gradle.api.internal.file;

import org.gradle.api.file.ImmutableFileAccessPermissions;
import org.gradle.api.internal.lambdas.SerializableLambdas;
import org.gradle.api.internal.provider.Providers;
import org.gradle.api.provider.Provider;

public abstract class AbstractImmutableFileAccessPermissions implements ImmutableFileAccessPermissions {

    @Override
    public Provider<Integer> toUnixNumeric() {
        AbstractImmutableUserClassFilePermissions user = (AbstractImmutableUserClassFilePermissions) getUser();
        AbstractImmutableUserClassFilePermissions group = (AbstractImmutableUserClassFilePermissions) getGroup();
        AbstractImmutableUserClassFilePermissions other = (AbstractImmutableUserClassFilePermissions) getOther();
        boolean hasTaskDependencies = user.hasTaskDependencies() || group.hasTaskDependencies() || other.hasTaskDependencies();
        if (hasTaskDependencies) {
            return user.toUnixNumeric().map(SerializableLambdas.transformer(u -> 64 * u))
                .zip(group.toUnixNumeric().map(SerializableLambdas.transformer(g -> 8 * g)), SerializableLambdas.bifunction(Integer::sum))
                .zip(other.toUnixNumeric(), SerializableLambdas.bifunction(Integer::sum));
        } else {
            return Providers.of(64 * user.toUnixNumeric().get() + 8 * group.toUnixNumeric().get() + other.toUnixNumeric().get());
        }
    }

    @SuppressWarnings("OctalInteger")
    protected static int getUserPartOf(int unixNumeric) {
        return (unixNumeric & 0_700) >> 6;
    }

    @SuppressWarnings("OctalInteger")
    protected static int getGroupPartOf(int unixNumeric) {
        return (unixNumeric & 0_070) >> 3;
    }

    @SuppressWarnings("OctalInteger")
    protected static int getOtherPartOf(int unixNumeric) {
        return unixNumeric & 0_007;
    }

}
