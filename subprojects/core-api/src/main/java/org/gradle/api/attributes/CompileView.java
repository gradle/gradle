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
 * Represents the depth at which module dependencies are exposed during compilation. Differentiates
 * between dependencies required to compile a module and dependencies required to compile against it.
 * <p>
 * This attribute is only applicable when paired with the {@link Usage#JAVA_API} attribute.
 *
 * @since 7.6
 */
@Incubating
public interface CompileView extends Named {
    Attribute<CompileView> VIEW_ATTRIBUTE = Attribute.of("org.gradle.compile-view", CompileView.class);

    /**
     * The API of a Java module's compile classpath. These are the set of dependencies required to
     * compile against a Java library.
     *
     * @since 7.6
     */
    String JAVA_API = "java-api";

    /**
     * The complete classpath of a Java module. These are the set of dependencies required to compile
     * a Java library itself.
     *
     * @since 7.6
     */
    String JAVA_COMPLETE = "java-complete";
}
