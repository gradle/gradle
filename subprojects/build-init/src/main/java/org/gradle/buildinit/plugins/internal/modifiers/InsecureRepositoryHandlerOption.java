/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.buildinit.plugins.internal.modifiers;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public enum InsecureRepositoryHandlerOption implements WithIdentifier {
    ALLOW("allow"),
    UPGRADE("upgrade"),
    FAIL("fail");

    public static InsecureRepositoryHandlerOption defaultOption = FAIL;

    private final String displayName;

    public static List<String> listSupported() {
        return Arrays.stream(values())
                     .map(InsecureRepositoryHandlerOption::getId)
                     .collect(Collectors.toList());
    }

    @Nullable
    public static InsecureRepositoryHandlerOption byId(String id) {
        return Arrays.stream(values())
                     .filter(o -> o.getId().equalsIgnoreCase(id))
                     .findFirst()
                     .orElse(null);
    }

    InsecureRepositoryHandlerOption(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String getId() {
        return Names.idFor(this);
    }

    @Override
    public String toString() {
        return displayName;
    }
}
