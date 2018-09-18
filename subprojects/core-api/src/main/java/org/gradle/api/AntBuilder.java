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
     * Imports an Ant build into the associated Gradle project, potentially providing alternative names for Gradle tasks that correspond to Ant targets.
     * <p>
     * For each Ant target that is to be converted to a Gradle task, the given {@code taskNamer} receives the Ant target name as input
     * and is expected to return the desired name for the corresponding Gradle task.
     * The transformer may be called multiple times with the same input.
     * Implementations should ensure uniqueness of the return value for a distinct input.
     * That is, no two inputs should yield the same return value.
     *  @param antBuildFile The build file. This is resolved as per {@link org.gradle.api.Project#file(Object)}.
     * @param taskNamer A transformer that calculates the name of the Gradle task for a corresponding Ant target.
     */
    @Incubating
    public abstract void importBuild(Object antBuildFile, Transformer<? extends String, ? super String> taskNamer);

    /**
     * Returns this AntBuilder. Useful when you need to pass this builder to methods from within closures.
     * @return this
     */
    public AntBuilder getAnt() {
        return this;
    }

    /**
     * Sets the Ant message priority that should correspond to the Gradle "lifecycle" log level.  Any messages logged at this
     * priority (or more critical priority) will be logged at least at lifecycle in Gradle's logger.  If the Ant priority already maps to a
     * higher Gradle log level, it will continue to be logged at that level.
     *
     * @param logLevel The Ant log level to map to the Gradle lifecycle log level
     */
    public abstract void setLifecycleLogLevel(AntMessagePriority logLevel);

    /**
     * Sets the Ant message priority that should correspond to the Gradle "lifecycle" log level.  Any messages logged at this
     * priority (or more critical priority) will be logged at least at lifecycle in Gradle's logger.  If the Ant priority already maps to a
     * higher Gradle log level, it will continue to be logged at that level.  Acceptable values are "VERBOSE", "DEBUG", "INFO", "WARN",
     * and "ERROR".
     *
     * @param logLevel The Ant log level to map to the Gradle lifecycle log level
     */
    public void setLifecycleLogLevel(String logLevel) {
        setLifecycleLogLevel(AntMessagePriority.valueOf(logLevel));
    }

    /**
     * Returns the Ant message priority that corresponds to the Gradle "lifecycle" log level.
     *
     * @return logLevel The Ant log level that maps to the Gradle lifecycle log level
     */
    public abstract AntMessagePriority getLifecycleLogLevel();

    /**
     * Represents the normal Ant message priorities.
     */
    public enum AntMessagePriority {
        DEBUG, VERBOSE, INFO, WARN, ERROR;

        public static AntMessagePriority from(int messagePriority) {
            switch(messagePriority) {
                case org.apache.tools.ant.Project.MSG_ERR:
                    return ERROR;
                case org.apache.tools.ant.Project.MSG_WARN:
                    return WARN;
                case org.apache.tools.ant.Project.MSG_INFO:
                    return INFO;
                case org.apache.tools.ant.Project.MSG_VERBOSE:
                    return VERBOSE;
                case org.apache.tools.ant.Project.MSG_DEBUG:
                    return DEBUG;
                default:
                    throw new IllegalArgumentException();
            }
        }
    }
}
