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

package org.gradle.model.dsl.internal.transform;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface RuleMetadata {
    /**
     * Definite input references, should be treated as absolute model paths.
     */
    String[] absoluteInputPaths() default {};

    int[] absoluteInputLineNumbers() default {};

    /**
     * Candidate input references, should be resolved relative to the subject of the rule.
     */
    String[] relativeInputPaths() default {};

    int[] relativeInputLineNumbers() default {};

    String scriptSourceDescription();

    int lineNumber();

    int columnNumber();

    String absoluteScriptSourceLocation();
}
