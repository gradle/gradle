/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.nativeplatform.toolchain.internal.msvcpp;

import org.gradle.util.internal.CollectionUtils;

import java.util.List;
import java.util.function.Function;

class EscapeUserArgs implements Function<String, String> {
    public static String escapeUserArg(String original) {
        return new EscapeUserArgs().apply(original);
    }

    public static List<String> escapeUserArgs(List<String> original) {
        return new EscapeUserArgs().applyToAll(original);
    }

    @Override
    public String apply(String original) {
        return original.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private List<String> applyToAll(List<String> args) {
        return CollectionUtils.collect(args, this);
    }
}
