/*
 * Copyright 2013 the original author or authors.
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

import java.util.List;

public class StaticOptionDescriptor implements OptionDescriptor {
    private final String name;
    private final Option option;
    private final OptionElement optionElement;

    public StaticOptionDescriptor(String name, Option option, OptionElement optionElement) {
        this.name = name;
        this.option = option;
        this.optionElement = optionElement;
    }

    public OptionElement getOptionElement(){
        return optionElement;
    }

    public String getName() {
        return name;
    }

    public Class getArgumentType() {
        return optionElement.getOptionType();
    }

    public List<String> getAvailableValues() {
        return optionElement.getAvailableValues();
    }

    public Option getOption() {
        return option;
    }

    public String getDescription() {
        return getOption().description();
    }

    public void apply(Object object, List<String> parameterValues) {
        optionElement.apply(object, parameterValues);
    }

    public int compareTo(OptionDescriptor o) {
        return getName().compareTo(o.getName());
    }
}
