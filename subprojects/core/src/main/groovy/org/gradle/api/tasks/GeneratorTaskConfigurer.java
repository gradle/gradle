/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.api.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.internal.tasks.generator.ConfigurationTarget;

/**
 * A {@code GeneratorTaskConfigurer} configures necessary domain object for {@code GeneratorTask}.
 * To define GeneratorTaskConfigurer correctly you should:
 * <ul>
 * <li>set your GeneratorTask instance to configurationTarget property.
 * <li>make sure the GeneratorTask instance depends on GeneratorTaskConfigurer instance.
 * </ul>
 * <p>
 * @author Szczepan Faber, @date: 19.03.11
 */
public class GeneratorTaskConfigurer extends DefaultTask {

    ConfigurationTarget configurationTarget;

    @TaskAction
    public void configure() {
        configurationTarget.configureDomainObject();
    }

    public ConfigurationTarget getConfigurationTarget() {
        return configurationTarget;
    }

    public void setConfigurationTarget(ConfigurationTarget configurationTarget) {
        this.configurationTarget = configurationTarget;
    }
}