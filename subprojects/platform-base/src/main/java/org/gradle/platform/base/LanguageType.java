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
 * The following example demonstrates how to register a custom language type using a plugin with a
 * {@link LanguageType} annotation.
 *
 * <pre autoTested=''>
 * import org.gradle.model.*
 * import org.gradle.model.collection.*
 *
 * interface CustomLanguageSourceSet extends LanguageSourceSet {}
 * class DefaultCustomLanguageSourceSet extends BaseLanguageSourceSet implements CustomLanguageSourceSet {}
 *
 * apply plugin: MySamplePlugin
 *
 * class MySamplePlugin extends RuleSource {
 *     {@literal @}LanguageType
 *     void declareCustomLanguage(LanguageTypeBuilder<CustomLanguageSourceSet> builder) {
 *         builder.setLanguageName("custom")
 *         builder.defaultImplementation(DefaultCustomLanguageSourceSet)
 *     }
 * }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Incubating
public @interface LanguageType {
}
