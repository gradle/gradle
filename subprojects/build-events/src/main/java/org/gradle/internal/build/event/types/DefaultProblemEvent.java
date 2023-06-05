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

import org.gradle.api.NonNullApi;
import org.gradle.api.problems.interfaces.Problem;
import org.gradle.api.problems.interfaces.ProblemLocation;
import org.gradle.tooling.internal.protocol.InternalProblemEvent;
import org.gradle.tooling.internal.protocol.events.InternalProblemDescriptor;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

@NonNullApi
public class DefaultProblemEvent extends AbstractProgressEvent<InternalProblemDescriptor> implements Serializable, InternalProblemEvent {

    private final Map<String, String> rawAttributes;

    public DefaultProblemEvent(Map<String, String> rawAttributes, InternalProblemDescriptor descriptor) {
        super(System.currentTimeMillis(), descriptor);
        this.rawAttributes = rawAttributes;
    }

    @Override
    public Map<String, String> getRawAttributes() {
        return rawAttributes;
    }

    public static Map<String, String> createRawAttributes(Problem problem) {
        Map<String, String> rawAttributes = new HashMap<>();
        rawAttributes.put("id", problem.getProblemId().getId());
        rawAttributes.put("message", problem.getMessage());
        rawAttributes.put("severity", problem.getSeverity().toString());
        ProblemLocation where = problem.getWhere();
        if (where != null) {
            String path = where.getPath();
            if (path != null) {
                rawAttributes.put("path", path);
            }
            Integer line = where.getLine();
            if (line != null) {
                rawAttributes.put("line", line.toString());
            }
        }
        String doc = problem.getDocumentationLink();
        if (doc != null) {
            rawAttributes.put("doc", doc);
        }

        String description = problem.getDescription();
        if (description != null) {
            rawAttributes.put("description", description);
        }

        int i = 1;
        for (String solution : problem.getSolutions()) {
            rawAttributes.put("solution" + i, solution);
            i++;
        }
        return rawAttributes;
    }

    @Override
    public String getDisplayName() {
        return "Problem kdkdkd";
    }
}
