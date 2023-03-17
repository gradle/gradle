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

package org.gradle.api.internal.tasks;

import com.google.common.annotations.VisibleForTesting;
import org.gradle.api.internal.tasks.options.BooleanOptionElement;
import org.gradle.api.internal.tasks.options.BuiltInOptionElement;
import org.gradle.api.internal.tasks.options.InstanceOptionDescriptor;
import org.gradle.api.internal.tasks.options.OptionDescriptor;
import org.gradle.api.internal.tasks.options.OptionElement;
import org.gradle.api.internal.tasks.options.OptionReader;
import org.gradle.api.specs.Specs;
import org.gradle.internal.Pair;
import org.gradle.util.internal.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class is responsible for supplying the built-in options and
 * the {@link BooleanOptionElement mutually exclusive options} of a task,
 * based on the task options provided by the {@link OptionReader}.
 */
public class TaskOptionsSupplier {
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskOptionsSupplier.class);

    @VisibleForTesting
    static final List<BuiltInOptionElement> BUILT_IN_OPTIONS = Stream.of(
        new BuiltInOptionElement(
            "Causes the task to be re-run even if up-to-date.",
            "rerun",
            task -> task.getOutputs().upToDateWhen(Specs.satisfyNone())
        )
    ).collect(Collectors.toList());

    /**
     * Builds a list of implicit built-in options available for every task.
     */
    private static List<OptionDescriptor> getBuiltInOptions(Object target, Collection<String> reserved) {
        List<OptionDescriptor> allBuiltInOptions = BUILT_IN_OPTIONS.stream().map(optionElement ->
            new InstanceOptionDescriptor(target, optionElement, null, reserved.contains(optionElement.getOptionName()))
        ).collect(Collectors.toList());
        List<OptionDescriptor> validBuiltInOptions = new ArrayList<>();
        for (OptionDescriptor builtInOption : allBuiltInOptions) {
            // built-in options only enabled if they do not clash with task-declared ones
            if (builtInOption.isClashing()) {
                LOGGER.warn("Built-in option '{}' in task {} was disabled for clashing with another option of same name", builtInOption.getName(), target);
            } else {
                validBuiltInOptions.add(builtInOption);
            }
        }
        return validBuiltInOptions;
    }

    /**
     * Builds a map of generated opposite options and
     * add pairs of mutually exclusive options to the {@link TaskOptions taskOptions}.
     */
    private static Map<String, OptionDescriptor> getOppositeOptions(Object target, Map<String, OptionDescriptor> options, TaskOptions taskOptions) {
        Map<String, OptionDescriptor> oppositeOptions = new HashMap<>();
        List<Pair<OptionDescriptor, OptionDescriptor>> mutuallyExclusiveOptions = new LinkedList<>();

        for (OptionDescriptor option : options.values()) {
            if (option instanceof InstanceOptionDescriptor) {
                OptionElement optionElement = ((InstanceOptionDescriptor) option).getOptionElement();
                if (optionElement instanceof BooleanOptionElement) {
                    BooleanOptionElement oppositeOptionElement = BooleanOptionElement.oppositeOf((BooleanOptionElement) optionElement);
                    String oppositeOptionName = oppositeOptionElement.getOptionName();
                    if (options.containsKey(oppositeOptionName)) {
                        LOGGER.warn("Opposite option '{}' in task {} was disabled for clashing with another option of same name", oppositeOptionName, target);
                    } else {
                        OptionDescriptor oppositeOption = new InstanceOptionDescriptor(target, oppositeOptionElement, null);
                        oppositeOptions.put(oppositeOptionName, oppositeOption);
                        mutuallyExclusiveOptions.add(Pair.of(option, oppositeOption));
                    }
                }
            }
        }
        taskOptions.mutuallyExclusiveOptions = Collections.unmodifiableList(mutuallyExclusiveOptions);
        return oppositeOptions;
    }

    public static TaskOptions get(Object target, OptionReader optionReader) {
        TaskOptions taskOptions = new TaskOptions();
        Map<String, OptionDescriptor> options = optionReader.getOptions(target);
        options.putAll(getOppositeOptions(target, options, taskOptions));
        List<OptionDescriptor> sortedOptions = CollectionUtils.sort(options.values());
        sortedOptions.addAll(getBuiltInOptions(target, options.keySet()));
        taskOptions.allTaskOptions = Collections.unmodifiableList(sortedOptions);
        return taskOptions;
    }

    public static class TaskOptions {
        private List<OptionDescriptor> allTaskOptions;
        private List<Pair<OptionDescriptor, OptionDescriptor>> mutuallyExclusiveOptions;
        private TaskOptions() {}

        public List<OptionDescriptor> getAll() {
            return allTaskOptions;
        }

        public List<Pair<OptionDescriptor, OptionDescriptor>> getMutuallyExclusive() {
            return mutuallyExclusiveOptions;
        }
    }
}
