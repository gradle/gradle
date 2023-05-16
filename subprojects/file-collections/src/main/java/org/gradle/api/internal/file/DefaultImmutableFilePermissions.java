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

import org.gradle.api.file.ImmutableUserClassFilePermissions;

public class DefaultImmutableFilePermissions extends AbstractImmutableFilePermissions {

    private final ImmutableUserClassFilePermissions user;

    private final ImmutableUserClassFilePermissions group;

    private final ImmutableUserClassFilePermissions other;

    public DefaultImmutableFilePermissions(int unixNumeric) {
        user = new DefaultImmutableUserClassFilePermissions(getUserPartOf(unixNumeric));
        group = new DefaultImmutableUserClassFilePermissions(getGroupPartOf(unixNumeric));
        other = new DefaultImmutableUserClassFilePermissions(getOtherPartOf(unixNumeric));
    }

    @Override
    public ImmutableUserClassFilePermissions getUser() {
        return user;
    }

    @Override
    public ImmutableUserClassFilePermissions getGroup() {
        return group;
    }

    @Override
    public ImmutableUserClassFilePermissions getOther() {
        return other;
    }
}
