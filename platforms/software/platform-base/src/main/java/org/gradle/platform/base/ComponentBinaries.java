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
 * Declares the binaries that should be built for a custom {@link org.gradle.platform.base.ComponentSpec} type.
 *
 * The following example demonstrates how to register a binary for a custom component type using a plugin with a
 * {@link org.gradle.platform.base.ComponentBinaries} annotation.
 * Furthermore the plugin registers 'DefaultSampleBinary' as implementation for {@link org.gradle.platform.base.BinarySpec}.
 *
 * <pre class='autoTested'>
 * interface SampleComponent extends VariantComponentSpec {}
 * interface SampleBinary extends BinarySpec {}
 * class DefaultSampleBinary extends BaseBinarySpec implements SampleBinary {}
 *
 * apply plugin: MyCustomBinariesPlugin
 *
 * class MyCustomBinariesPlugin extends RuleSource {
 *     {@literal @}ComponentType
 *     void register(TypeBuilder&lt;SampleBinary&gt; builder) {
 *         builder.defaultImplementation(DefaultSampleBinary)
 *     }
 *
 *     {@literal @}ComponentBinaries
 *     void createBinariesForSampleLibrary(ModelMap&lt;SampleBinary&gt; binaries, SampleComponent component) {
 *         binaries.create("${component.name}Binary", SampleBinary)
 *     }
 * }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Incubating
public @interface ComponentBinaries {
}
