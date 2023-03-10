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

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class is responsible for adding built-in task options and
 * {@link BooleanOptionElement boolean opposite options} to the list of options
 * provided by the {@link OptionReader}.
 */
public class TaskOptionSupplier {

    private static final Logger LOGGER = LoggerFactory.getLogger(TaskOptionSupplier.class);

    @VisibleForTesting
    static final Map<String, BuiltInOptionElement> BUILT_IN_OPTIONS = Stream.of(
        new BuiltInOptionElement(
            "Causes the task to be re-run even if up-to-date.",
            "rerun",
            task -> task.getOutputs().upToDateWhen(Specs.satisfyNone())
        )
    ).collect(Collectors.toMap(BuiltInOptionElement::getOptionName, Function.identity(), (a, b) -> a, LinkedHashMap::new));

    /**
     * Builds a list of implicit built-in options available for every task.
     */
    private static List<OptionDescriptor> getBuiltInOptions(Object target, Collection<String> reserved) {
        return BUILT_IN_OPTIONS.values().stream().map(optionElement ->
            new InstanceOptionDescriptor(target, optionElement, null, reserved.contains(optionElement.getOptionName()))
        ).collect(Collectors.toList());
    }

    private static Map<String, OptionDescriptor> getOppositeOptions(Object target, Collection<OptionDescriptor> options) {
        Map<String, OptionDescriptor> optionMap = new HashMap<>();
        options.forEach(optionDescriptor -> optionMap.put(optionDescriptor.getName(), optionDescriptor));
        Map<String, OptionDescriptor> oppositeOptions = new HashMap<>();

        for (OptionDescriptor optionDescriptor : options) {
            if (optionDescriptor instanceof InstanceOptionDescriptor) {
                OptionElement optionElement = ((InstanceOptionDescriptor) optionDescriptor).getOptionElement();
                if (optionElement instanceof BooleanOptionElement) {
                    BooleanOptionElement oppositeOptionElement = BooleanOptionElement.oppositeOf((BooleanOptionElement) optionElement);
                    String oppositeOptionName = oppositeOptionElement.getOptionName();
                    if (optionMap.containsKey(oppositeOptionName)) {
                        LOGGER.warn("Opposite option '{}' was disabled for clashing with another option of same name", oppositeOptionName);
                    } else {
                        oppositeOptions.put(oppositeOptionName, new InstanceOptionDescriptor(target, oppositeOptionElement, null));
                    }
                }
            }
        }
        return oppositeOptions;
    }

    public static List<OptionDescriptor> get(Object target, OptionReader optionReader) {
        return get(target, optionReader, true);
    }

    public static List<OptionDescriptor> get(Object target, OptionReader optionReader, boolean validOnly) {
        Map<String, OptionDescriptor> options = optionReader.getOptions(target);
        options.putAll(getOppositeOptions(target, options.values()));
        List<OptionDescriptor> taskOptions = CollectionUtils.sort(options.values());
        for (OptionDescriptor builtInOption : getBuiltInOptions(target, options.keySet())) {
            // built-in options only enabled if they do not clash with task-declared ones
            if (!validOnly || !builtInOption.isClashing()) {
                taskOptions.add(builtInOption);
            }
        }
        return taskOptions;
    }
}
