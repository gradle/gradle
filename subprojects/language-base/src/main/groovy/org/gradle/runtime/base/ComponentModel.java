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

package org.gradle.runtime.base;

import org.gradle.api.Incubating;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that the associated class declares a ComponentModel
 *
 * The following example demonstrates how to register a custom component using a plugin with a
 * ComponentModel annotation. Furthermore the plugin creates an instance of SampleLibrary named
 * 'sampleLib'.
 *
 * <pre autoTested=''>
 * import org.gradle.model.*
 * import org.gradle.model.collection.*
 *
 * interface SampleLibrary extends LibrarySpec {}
 * class DefaultSampleLibrary extends DefaultLibrarySpec implements SampleLibrary {}
 *
 * apply plugin: MySamplePlugin
 *
 * class MySamplePlugin implements Plugin<Project> {
 *     void apply(final Project project) {}
 *
 *     @RuleSource
 *     @ComponentModel(type = SampleLibrary.class, implementation = DefaultSampleLibrary.class)
 *     static class Rules {
 *         @Mutate
 *         void createSampleLibraryComponents(NamedItemCollectionBuilder<SampleLibrary> componentSpecs) {
 *             componentSpecs.create("sampleLib")
 *         }
 *     }
 * }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Incubating
public @interface ComponentModel {
    /**
     * Denotes the type of the LibrarySpec.
     */
    Class<? extends LibrarySpec> type();

    /**
     * Denotes the implementation class of the LibrarySpec.
     */
    Class<? extends LibrarySpec> implementation();
}