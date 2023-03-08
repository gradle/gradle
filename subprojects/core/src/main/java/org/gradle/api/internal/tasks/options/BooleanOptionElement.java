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

import org.gradle.api.tasks.options.Option;
import org.gradle.internal.typeconversion.TypeConversionException;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * A flag, does not take an argument.
 */
public class BooleanOptionElement extends AbstractOptionElement {
    private final PropertySetter setter;

    private BooleanOptionElement(String optionName, Option option, PropertySetter setter) {
        super(optionName, option, Void.TYPE, setter.getDeclaringClass());
        this.setter = setter;
    }

    private BooleanOptionElement(String optionName, String optionDescription, PropertySetter setter) {
        super(optionDescription, optionName, Void.TYPE);
        this.setter = setter;
    }

    public static BooleanOptionElement of(String optionName, Option option, PropertySetter setter) {
        if (isDisableOption(optionName)) {
            return new BooleanOptionElement(optionName, "Disables option --" + optionName.substring(3), setter);
        } else {
            return new BooleanOptionElement(optionName, option, setter);
        }
    }

    @Override
    public Set<String> getAvailableValues() {
        return Collections.emptySet();
    }

    @Override
    public void apply(Object object, List<String> parameterValues) throws TypeConversionException {
        if (isDisableOption(getOptionName())) {
            setter.setValue(object, Boolean.FALSE);
        } else {
            setter.setValue(object, Boolean.TRUE);
        }
    }

    public static boolean isDisableOption(String optionName) {
        return optionName.startsWith("no-");
    }
}
