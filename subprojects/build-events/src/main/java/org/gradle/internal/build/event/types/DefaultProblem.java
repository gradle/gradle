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

package org.gradle.internal.build.event.types;

import org.gradle.tooling.internal.protocol.InternalProblem;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DefaultProblem implements Serializable, InternalProblem {

    private final Map<String, String> rawAttributes;

    private DefaultProblem(Map<String, String> rawAttributes) {
        this.rawAttributes = rawAttributes;
    }


    @Override
    public Map<String, String> getRawAttributes() {
        return rawAttributes;
    }

    public static InternalProblem from(Map<String, String> rawAttributes) {
        return new DefaultProblem(rawAttributes);
    }

    @SuppressWarnings("unchecked")
    public static List<InternalProblem> from(List<Object> problems) {
        List<InternalProblem> result = new ArrayList<>(problems.size());
        for (Object problem : problems) {
            if (problem instanceof Map) {
                result.add(DefaultProblem.from((Map<String, String>) problem));
            } else {
                throw new RuntimeException("Not the droids I'm looking for.");
            }
        }
        return result;
    }
}
