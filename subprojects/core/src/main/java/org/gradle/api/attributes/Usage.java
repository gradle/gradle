/*
 * Copyright 2016 the original author or authors.
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
import org.gradle.api.internal.model.DefaultObjectFactory;

/**
 * Represents the usage of a configuration. Typical usages include compilation or runtime.
 * This interface allows the user to customize usages by implementing this interface.
 *
 * @since 3.4
 */
@Incubating
public interface Usage extends Named {
    Attribute<Usage> USAGE_ATTRIBUTE = Attribute.of(Usage.class);

    Usage FOR_COMPILE = DefaultObjectFactory.INSTANCE.named(Usage.class, "for compile");
    Usage FOR_RUNTIME = DefaultObjectFactory.INSTANCE.named(Usage.class, "for runtime");

    /**
     * The Java API of a library, packaged as class path elements, either a JAR or a classes directory.
     *
     * @since 4.0
     */
    String JAVA_API = "java-api";

    /**
     * The Java API of a library, packaged as class path elements, either a JAR or a classes directory. Should not include resources, but may.
     *
     * @since 4.0
     */
    String JAVA_API_CLASSES = "java-api-classes";

    /**
     * The Java runtime of a component, packaged as class path elements, either a JAR or a classes directory.
     *
     * @since 4.0
     */
    String JAVA_RUNTIME = "java-runtime";

    /**
     * The Java runtime of a component, packaged as JAR only. Must not include classes directories.
     *
     * @since 4.0
     */
    String JAVA_RUNTIME_JARS = "java-runtime-jars";

    /**
     * The Java runtime classes of a component, packaged as class path elements, either a JAR or a classes directory. Should not include resources, but may.
     *
     * @since 4.0
     */
    String JAVA_RUNTIME_CLASSES = "java-runtime-classes";

    /**
     * The Java runtime resources of a component, packaged as class path elements, either a JAR or a classes directory. Should not include classes, but may.
     *
     * @since 4.0
     */
    String JAVA_RUNTIME_RESOURCES = "java-runtime-resources";
}
