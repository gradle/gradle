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
package org.gradle.integtests.tooling.fixture;

import java.lang.annotation.*;

/**
 * Specifies the range of target Gradle versions that the given tooling API test can work with.
 * <p>
 * A default version range is defined on the ToolingApiSpecification, which can be overridden by a separate annotation placed on a test task or test method.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Inherited
public @interface TargetGradleVersion {
    /**
     * The requested target Gradle version. Can use:
     * <ul>
     *     <li>{@code >=nnn}</li>
     *     <li>{@code <=nnn}</li>
     *     <li>{@code <nnn}</li>
     *     <li>{@code >nnn}</li>
     *     <li>{@code =nnn}</li>
     *     <li>{@code current}</li>
     *     <li>{@code !current}</li>
     *     <li>space-separated list of patterns</li>
     * </ul>
     */
    String value();
}
