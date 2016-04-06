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
 * Declares the tasks to build a custom {@link org.gradle.platform.base.BinarySpec} binary.
 *
 * The following example demonstrates how to register multiple tasks for custom binary using a plugin with a
 * {@link org.gradle.platform.base.BinaryTasks} annotation.
 *
 * <pre autoTested='true'>
 * {@literal @}Managed interface SampleComponent extends ComponentSpec {}
 * {@literal @}Managed interface SampleBinary extends BinarySpec {}
 *
 * apply plugin: MyCustomBinariesPlugin
 *
 * class MyCustomBinaryCreationTask extends DefaultTask {
 *      {@literal @}TaskAction void build() {
 *          //building the binary
 *      }
 * }
 *
 * class MyCustomBinariesPlugin extends RuleSource {
 *     {@literal @}ComponentType
 *     void register(TypeBuilder&lt;SampleBinary&gt; builder) {}
 *
 *     {@literal @}BinaryTasks
 *     void createBinaryTasks(ModelMap&lt;Task&gt; tasks, SampleBinary binary) {
 *         tasks.create("${binary.name}Task1", MyCustomBinaryCreationTask)
 *         tasks.create("${binary.name}Task2") {
 *             dependsOn "${binary.name}Task1"
 *         }
 *     }
 * }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Incubating
public @interface BinaryTasks {
}
