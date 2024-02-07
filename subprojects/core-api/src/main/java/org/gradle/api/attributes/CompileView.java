/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.attributes;

import org.gradle.api.Incubating;
import org.gradle.api.Named;

/**
 * Differentiates between the different views of a library's compile classpath.
 *
 * <p>This attribute is only applicable when paired with the {@link Usage#JAVA_API} attribute.</p>
 *
 * @since 8.8
 */
@Incubating
public interface CompileView extends Named {

    /**
     * The attribute that tracks a variant's compile view.
     *
     * @since 8.8
     */
    Attribute<CompileView> VIEW_ATTRIBUTE = Attribute.of("org.gradle.compile-view", CompileView.class);

    /**
     * The API of a Java library's compile classpath. These are the set of dependencies required to
     * compile against a Java library's public API.
     *
     * @since 8.8
     */
    String JAVA_API = "java-api";

    /**
     * The implementation compile view of a Java library. These are the set of dependencies required to compile
     * a Java library itself, including all internal and public classes.
     *
     * @since 8.8
     */
    String JAVA_IMPLEMENTATION = "java-implementation";
}
