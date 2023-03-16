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
 * This class is responsible for adding built-in task options and
 * {@link BooleanOptionElement boolean opposite options} to the options
 * provided by the {@link OptionReader}.
 */
public class TaskOptionSupplier {
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskOptionSupplier.class);
    private final Object target;
    private final OptionReader optionReader;
    private final List<OptionDescriptor> oppositeOptionPairs = new LinkedList<>();

    @VisibleForTesting
    static final List<BuiltInOptionElement> BUILT_IN_OPTIONS = Stream.of(
        new BuiltInOptionElement(
            "Causes the task to be re-run even if up-to-date.",
            "rerun",
            task -> task.getOutputs().upToDateWhen(Specs.satisfyNone())
        )
    ).collect(Collectors.toList());

    public TaskOptionSupplier(Object target, OptionReader optionReader) {
        this.target = target;
        this.optionReader = optionReader;
    }

    /**
     * Builds a list of implicit built-in options available for every task.
     */
    private List<OptionDescriptor> getBuiltInOptions(Collection<String> reserved) {
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
     * writes each opposite option pair to {@link #oppositeOptionPairs}.
     */
    private Map<String, OptionDescriptor> getOppositeOptions(Map<String, OptionDescriptor> options) {
        Map<String, OptionDescriptor> oppositeOptions = new HashMap<>();

        for (OptionDescriptor optionDescriptor : options.values()) {
            if (optionDescriptor instanceof InstanceOptionDescriptor) {
                OptionElement optionElement = ((InstanceOptionDescriptor) optionDescriptor).getOptionElement();
                if (optionElement instanceof BooleanOptionElement) {
                    BooleanOptionElement oppositeOptionElement = BooleanOptionElement.oppositeOf((BooleanOptionElement) optionElement);
                    String oppositeOptionName = oppositeOptionElement.getOptionName();
                    if (options.containsKey(oppositeOptionName)) {
                        LOGGER.warn("Opposite option '{}' was disabled for clashing with another option of same name", oppositeOptionName);
                    } else {
                        oppositeOptions.put(oppositeOptionName, new InstanceOptionDescriptor(target, oppositeOptionElement, null));
                        addOppositeOptionPair(optionDescriptor, oppositeOptionElement);
                    }
                }
            }
        }
        return oppositeOptions;
    }

    private void addOppositeOptionPair(OptionDescriptor optionDescriptor, BooleanOptionElement oppositeOptionElement) {
        oppositeOptionPairs.add(optionDescriptor);
        oppositeOptionPairs.add(new InstanceOptionDescriptor(target, oppositeOptionElement, null));
    }

    public List<OptionDescriptor> getAllOptions() {
        Map<String, OptionDescriptor> options = optionReader.getOptions(target);
        options.putAll(getOppositeOptions(options));
        List<OptionDescriptor> taskOptions = CollectionUtils.sort(options.values());
        taskOptions.addAll(getBuiltInOptions(options.keySet()));
        return taskOptions;
    }

    public List<OptionDescriptor> getOppositeOptionPairs() {
        return Collections.unmodifiableList(oppositeOptionPairs);
    }
}
