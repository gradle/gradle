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

import org.gradle.api.file.ImmutableFilePermissions;

public abstract class AbstractImmutableFilePermissions implements ImmutableFilePermissions {

    @Override
    public int toUnixNumeric() {
        AbstractImmutableUserClassFilePermissions user = (AbstractImmutableUserClassFilePermissions) getUser();
        AbstractImmutableUserClassFilePermissions group = (AbstractImmutableUserClassFilePermissions) getGroup();
        AbstractImmutableUserClassFilePermissions other = (AbstractImmutableUserClassFilePermissions) getOther();
        return 64 * user.toUnixNumeric() + 8 * group.toUnixNumeric() + other.toUnixNumeric();
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
