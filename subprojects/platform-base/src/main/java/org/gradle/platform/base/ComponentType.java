/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.platform.base;

import org.gradle.api.Incubating;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that a custom {@link org.gradle.platform.base.ComponentSpec} type.
 *
 * The following example demonstrates how to register a custom component type using a plugin with a
 * {@link ComponentType} annotation.
 * Furthermore the plugin creates an instance of SampleComponent named 'sampleComponent'.
 *
 * <pre autoTested='true'>
 * import org.gradle.model.*
 * import org.gradle.model.collection.*
 *
 * interface SampleComponent extends ComponentSpec {}
 * class DefaultSampleComponent extends BaseComponentSpec implements SampleComponent {}
 *
 * apply plugin: MySamplePlugin
 *
 * class MySamplePlugin implements Plugin<Project> {
 *     void apply(final Project project) {}
 *
 *     {@literal @}RuleSource
 *     static class Rules {
 *         {@literal @}ComponentType
 *         void register(ComponentTypeBuilder<SampleComponent> builder) {
 *             builder.defaultImplementation(DefaultSampleComponent)
 *         }
 *
 *         {@literal @}Mutate
 *         void createSampleLibraryComponents(CollectionBuilder<SampleComponent> componentSpecs) {
 *             componentSpecs.create("sampleComponent")
 *         }
 *     }
 * }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Incubating
public @interface ComponentType {
}
