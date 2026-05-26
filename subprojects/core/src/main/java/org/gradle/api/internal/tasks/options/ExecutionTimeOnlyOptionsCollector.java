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

package org.gradle.api.internal.tasks.options;

import org.gradle.api.Task;
import org.gradle.api.internal.tasks.TaskOptionsGenerator;
import org.jspecify.annotations.NullMarked;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Walks a collection of configured tasks and collects the names of options declared
 * {@link org.gradle.api.tasks.options.Option#executionTimeOnly() executionTimeOnly = true}.
 */
@NullMarked
public final class ExecutionTimeOnlyOptionsCollector {

    private ExecutionTimeOnlyOptionsCollector() {}

    public static Set<String> collect(Iterable<? extends Task> tasks, OptionReader optionReader) {
        Set<String> names = new LinkedHashSet<>();
        for (Task task : tasks) {
            // A task with a malformed @Option / @OptionValues declaration would throw here.
            // We skip it: that task simply can't contribute to the manifest. The validation
            // error will still surface the next time the user actually invokes that option,
            // via the normal CLI option-parsing path. Crashing the whole configuration just
            // to populate a manifest of execution-time-only option names is the wrong tradeoff.
            TaskOptionsGenerator.TaskOptions options;
            try {
                options = TaskOptionsGenerator.generate(task, optionReader);
            } catch (OptionValidationException ignored) {
                continue;
            }
            for (OptionDescriptor descriptor : options.getAll()) {
                if (descriptor.isExecutionTimeOnly()) {
                    names.add(descriptor.getName());
                }
            }
        }
        return names;
    }
}
