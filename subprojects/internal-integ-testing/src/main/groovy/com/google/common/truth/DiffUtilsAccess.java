/*
 * Copyright 2022 the original author or authors.
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

package com.google.common.truth;

import java.util.List;

/**
 * This exists to allow us to use the {@code DiffUtils} class by calling package-level methods;
 * it must be located in the {code com.google.common.truth} package to be functional.
 */
public final class DiffUtilsAccess {
    public static DiffUtils getDiffUtils() {
        return new DiffUtils();
    }

    public static List<String> generateUnifiedDiff(List<String> original, List<String> revised, int contextSize) {
        return getDiffUtils().generateUnifiedDiff(original, revised, contextSize);
    }
}
