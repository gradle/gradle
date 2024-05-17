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
import org.gradle.tooling.events.internal.BaseProgressEvent;
import org.gradle.tooling.events.problems.ProblemDescriptor;
import org.gradle.tooling.events.problems.ProblemEvent;

@NonNullApi
public class DefaultProblemEvent extends BaseProgressEvent implements ProblemEvent {
    public DefaultProblemEvent(long eventTime, ProblemDescriptor problemDescriptor) {
        super(eventTime, problemDescriptor.getDisplayName(), problemDescriptor);
    }

    @Override
    public ProblemDescriptor getDescriptor() {
        return (ProblemDescriptor) super.getDescriptor();
    }

}
