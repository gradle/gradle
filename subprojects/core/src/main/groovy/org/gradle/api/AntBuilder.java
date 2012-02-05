/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api;

import java.util.Map;

/**
 * <p>An {@code AntBuilder} allows you to use Ant from your build script.</p>
 */
public abstract class AntBuilder extends groovy.util.AntBuilder {
    /**
     * Returns the properties of the Ant project. This is a live map, you that you can make changes to the map and these
     * changes are reflected in the Ant project.
     *
     * @return The properties. Never returns null.
     */
    public abstract Map<String, Object> getProperties();

    /**
     * Returns the references of the Ant project. This is a live map, you that you can make changes to the map and these
     * changes are reflected in the Ant project.
     *
     * @return The references. Never returns null.
     */
    public abstract Map<String, Object> getReferences();

    /**
     * Imports an Ant build into the associated Gradle project.
     *
     * @param antBuildFile The build file. This is resolved as per {@link Project#file(Object)}.
     */
    public abstract void importBuild(Object antBuildFile);

    /**
     * Returns this AntBuilder. Useful when you need to pass this builder to methods from within closures.
     * @return this
     */
    public AntBuilder getAnt() {
        return this;
    }
}
