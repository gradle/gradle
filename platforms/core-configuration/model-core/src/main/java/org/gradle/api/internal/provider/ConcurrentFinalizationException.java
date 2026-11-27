/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.provider;

import com.google.common.collect.ImmutableList;
import org.gradle.api.Describable;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.internal.deprecation.Documentation;
import org.gradle.internal.exceptions.ResolutionProvider;

import java.util.List;

public class ConcurrentFinalizationException extends InvalidUserCodeException implements ResolutionProvider {

    private static final String CONSULT_DOCUMENTATION_MESSAGE = Documentation.userManual("lazy_configuration", "sharing_properties_between_parallel_tasks").getConsultDocumentationMessage();

    private static final List<String> RESOLUTIONS = ImmutableList.of(
        "Do not share the same property or file collection between tasks that can run in parallel. Give each task its own property and connect it to the provider that supplies the value.",
        "If the value is available at configuration time, call 'finalizeValue()' on it before it is shared, for example when creating the build service that holds it.",
        "If tasks share the value through their inputs and the configuration cache is disabled, enable the configuration cache, so that each task works with its own copy of the value."
    );

    public ConcurrentFinalizationException(Describable displayName) {
        super(String.format(
            "Cannot finalize the value of %s because another thread is finalizing it. " +
                "Finalizing a value from multiple threads at once is not supported. %s",
            displayName.getDisplayName(),
            CONSULT_DOCUMENTATION_MESSAGE
        ));
    }

    @Override
    public List<String> getResolutions() {
        return RESOLUTIONS;
    }
}
