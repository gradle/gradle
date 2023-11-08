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

package org.gradle.tooling.events.problems.internal;

import org.gradle.api.NonNullApi;
import org.gradle.tooling.events.OperationDescriptor;
import org.gradle.tooling.events.internal.DefaultOperationDescriptor;
import org.gradle.tooling.events.problems.ProblemDescriptor;
import org.gradle.tooling.internal.protocol.events.InternalOperationDescriptor;

import java.util.Map;

@NonNullApi
public class DefaultProblemsOperationDescriptor extends DefaultOperationDescriptor implements ProblemDescriptor {
    private final String json;
    private final Map<String, Object> additionalData;

    public DefaultProblemsOperationDescriptor(
        InternalOperationDescriptor internalDescriptor,
        OperationDescriptor parent,
        String json,
        Map<String, Object> additionalData
    ) {
        super(internalDescriptor, parent);
        this.json = json;
        this.additionalData = additionalData;
    }

    @Override
    public String getJson() {
        return json;
    }

    @Override
    public Map<String, Object> getAdditionalData() {
        return additionalData;
    }
}
