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

import org.gradle.api.file.ImmutableFileAccessPermission;

public class DefaultImmutableFileAccessPermissions extends AbstractImmutableFileAccessPermissions {

    private final ImmutableFileAccessPermission user;

    private final ImmutableFileAccessPermission group;

    private final ImmutableFileAccessPermission other;

    public DefaultImmutableFileAccessPermissions(int unixNumeric) {
        user = new DefaultImmutableFileAccessPermission(getUserPartOf(unixNumeric));
        group = new DefaultImmutableFileAccessPermission(getGroupPartOf(unixNumeric));
        other = new DefaultImmutableFileAccessPermission(getOtherPartOf(unixNumeric));
    }

    @Override
    public ImmutableFileAccessPermission getUser() {
        return user;
    }

    @Override
    public ImmutableFileAccessPermission getGroup() {
        return group;
    }

    @Override
    public ImmutableFileAccessPermission getOther() {
        return other;
    }
}
