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
import org.gradle.api.Task;
import org.gradle.api.internal.tasks.options.BooleanOptionElement;
import org.gradle.api.internal.tasks.options.BuiltInOptionElement;
import org.gradle.api.internal.tasks.options.InstanceOptionDescriptor;
import org.gradle.api.internal.tasks.options.OptionDescriptor;
import org.gradle.api.internal.tasks.options.OptionElement;
import org.gradle.api.internal.tasks.options.OptionReader;
import org.gradle.api.specs.Specs;
import org.gradle.cli.CommandLineParser;
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

/**
 * This class is responsible for generating the built-in options and
 * the {@link BooleanOptionElement mutually exclusive options} of a task,
 * based on the task options provided by the {@link OptionReader}.
 */
public class TaskOptionsGenerator {
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskOptionsGenerator.class);

    @VisibleForTesting
    static final List<BuiltInOptionElement> BUILT_IN_OPTIONS = Collections.singletonList(
        new BuiltInOptionElement(
            "Causes the task to be re-run even if up-to-date.",
            "rerun",
            task -> task.getOutputs().upToDateWhen(Specs.satisfyNone())
        )
    );

    /**
     * Builds a list of implicit built-in options available for every task.
     */
    private static List<OptionDescriptor> generateBuiltInOptions(Object target, Collection<String> reserved) {
        List<OptionDescriptor> validBuiltInOptions = new ArrayList<>();
        for (BuiltInOptionElement builtInOption : BUILT_IN_OPTIONS) {
            OptionDescriptor optionDescriptor = new InstanceOptionDescriptor(target, builtInOption, null, reserved.contains(builtInOption.getOptionName()));
            if (optionDescriptor.isClashing()) {
                LOGGER.warn("Built-in option '{}' in task {} was disabled for clashing with another option of same name", optionDescriptor.getName(), target);
            } else {
                validBuiltInOptions.add(optionDescriptor);
            }
        }
        return validBuiltInOptions;
    }

    /**
     * Generates a map of opposite options and, based on these, adds {@link Pair pairs}
     * of mutually exclusive options to the {@link TaskOptions#mutuallyExclusiveOptions taskOptions} argument.
     */
    private static Map<String, OptionDescriptor> generateOppositeOptions(Object target, Map<String, OptionDescriptor> options, TaskOptions taskOptions) {
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

    /**
     * Generates the {@link TaskOptions taskOptions} for the given task.
     *
     * @param task the task, must implement {@link Task}
     * @param optionReader the optionReader
     * @return the generated taskOptions
     */
    public static TaskOptions generate(Object task, OptionReader optionReader) {
        TaskOptions taskOptions = new TaskOptions();
        Map<String, OptionDescriptor> options = optionReader.getOptions(task);
        options.putAll(generateOppositeOptions(task, options, taskOptions));
        List<OptionDescriptor> sortedOptions = CollectionUtils.sort(options.values(), BooleanOptionElement.groupOppositeOptions());
        sortedOptions.addAll(generateBuiltInOptions(task, options.keySet()));
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

        public void addMutualExclusions(CommandLineParser parser) {
            mutuallyExclusiveOptions.forEach(pair -> parser.allowOneOf(pair.getLeft().getName(), pair.getRight().getName()));
        }
    }
}
