/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.declarative.dsl.model.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * When applied to a member, excludes the member from declarative definition.
 * If the excluded member is overridden in a subtype, it becomes visible in that subtype unless annotated there, too.
 * <p>
 * When applied to a type, makes the type a hidden type. Hidden types do not contribute declarative members to subtypes and cannot appear in signatures of
 * declarative members. The supertypes of a hidden type become hidden types themselves and can no longer be used in declarative member signatures or as
 * supertypes of other visible types, either, unless annotated as {@link VisibleInDefinition}.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface HiddenInDefinition {}
