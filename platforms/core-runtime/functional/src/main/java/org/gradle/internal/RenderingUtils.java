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
package org.gradle.internal;

import java.util.Collection;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import static java.lang.String.join;

public abstract class RenderingUtils {
    public static String quotedOxfordListOf(Collection<String> values, String conjunction) {
        return values.stream()
            .sorted()
            .map(s -> "'" + s + "'")
            .collect(oxfordJoin(conjunction));
    }

    public static String oxfordListOf(Collection<String> values, String conjunction) {
        return values.stream()
            .collect(oxfordJoin(conjunction));
    }

    public static Collector<? super String, ?, String> oxfordJoin(String conjunction) {
        return Collectors.collectingAndThen(Collectors.toList(), stringList -> {
            switch (stringList.size()) {
                case 0:
                    return "";
                case 1:
                    return stringList.get(0);
                case 2:
                    return join(" " + conjunction + " ", stringList);
                default:
                    int bound = stringList.size() - 1;
                    return join(", ", stringList.subList(0, bound)) + ", " + conjunction + " " + stringList.get(bound);
            }
        });
    }
}
