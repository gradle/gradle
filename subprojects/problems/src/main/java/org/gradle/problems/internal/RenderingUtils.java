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
package org.gradle.problems.internal;

import java.util.Collection;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public abstract class RenderingUtils {
    public static String oxfordListOf(Collection<String> values, String conjunction) {
        return values.stream()
            .sorted()
            .map(s -> "'" + s + "'")
            .collect(oxfordJoin(conjunction));
    }

    public static Collector<? super String, ?, String> oxfordJoin(String conjunction) {
        return Collectors.collectingAndThen(Collectors.toList(), stringList -> {
            if (stringList.isEmpty()) {
                return "";
            }
            if (stringList.size() == 1) {
                return stringList.get(0);
            }
            int bound = stringList.size() - 1;
            return String.join(", ", stringList.subList(0, bound)) + " " + conjunction + " " + stringList.get(bound);
        });
    }
}
