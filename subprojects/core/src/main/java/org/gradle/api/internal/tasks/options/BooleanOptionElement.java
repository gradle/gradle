/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.api.internal.tasks.TaskOptionsGenerator;
import org.gradle.api.tasks.options.Option;
import org.gradle.internal.typeconversion.TypeConversionException;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * A flag, does not take an argument.
 *
 * If a command line option is provided, the {@link TaskOptionsGenerator} automatically creates an opposite option.
 * For example, {@code "--no-foo"} is created for the provided option {@code "--foo"} or {@code "--bar"} for the provided option {@code "--no-bar"}.
 *
 * Options whose names starts with "--no" are 'disable options' and set the option value to false.
 */
public class BooleanOptionElement extends AbstractOptionElement {
    private static final String DISABLE_DESC_PREFIX = "Disables option --";
    private static final String OPPOSITE_DESC_PREFIX = "Opposite option of --";
    private static final String DISABLE_NAME_PREFIX = "no-";
    private final PropertySetter setter;

    public BooleanOptionElement(String optionName, Option option, PropertySetter setter) {
        super(optionName, option, Void.TYPE, setter.getDeclaringClass());
        this.setter = setter;
    }

    private BooleanOptionElement(String optionName, String optionDescription, PropertySetter setter) {
        super(optionDescription, optionName, Void.TYPE);
        this.setter = setter;
    }

    public static BooleanOptionElement oppositeOf(BooleanOptionElement optionElement) {
        String optionName = optionElement.getOptionName();
        return optionElement.isDisableOption() ? new BooleanOptionElement(removeDisablePrefix(optionName), OPPOSITE_DESC_PREFIX + optionName, optionElement.setter)
            : new BooleanOptionElement(DISABLE_NAME_PREFIX + optionName, DISABLE_DESC_PREFIX + optionName, optionElement.setter);
    }

    /**
     * Returns a comparator that groups opposite option pairs together.
     *
     * <p>Options are sorted in the natural order of their names,
     * except for disable options which are sorted after their opposite option.
     * For example, {@code "--foo"} and {@code "--no-foo"} are grouped together
     * and are sorted after {@code "--bar"} and {@code "--no-bar"}.
     *
     * @return a comparator that groups opposite option pairs together
     */
    public static Comparator<OptionDescriptor> groupOppositeOptions() {
        return Comparator.comparing(optionDescriptor -> {
            if (optionDescriptor instanceof InstanceOptionDescriptor) {
                InstanceOptionDescriptor instanceOptionDescriptor = (InstanceOptionDescriptor) optionDescriptor;
                if (instanceOptionDescriptor.getOptionElement() instanceof BooleanOptionElement) {
                    BooleanOptionElement optionElement = (BooleanOptionElement) instanceOptionDescriptor.getOptionElement();
                    if (optionElement.isDisableOption()) {
                        return removeDisablePrefix(optionElement.getOptionName()) + "-";
                    }
                }
            }
            return optionDescriptor.getName();
        });
    }

    public boolean isDisableOption() {
        return this.getOptionName().startsWith(DISABLE_NAME_PREFIX);
    }

    @Override
    public Set<String> getAvailableValues() {
        return Collections.emptySet();
    }

    @Override
    public void apply(Object object, List<String> parameterValues) throws TypeConversionException {
        if (isDisableOption()) {
            setter.setValue(object, Boolean.FALSE);
        } else {
            setter.setValue(object, Boolean.TRUE);
        }
    }

    private static String removeDisablePrefix(String optionName) {
        return optionName.substring(DISABLE_NAME_PREFIX.length());
    }
}
