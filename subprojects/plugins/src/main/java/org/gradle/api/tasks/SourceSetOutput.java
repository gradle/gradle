/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.tasks;

import org.gradle.api.file.FileCollection;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Map;

/**
 * A collection of all output directories (compiled classes, processed resources, etc.) - notice that {@link SourceSetOutput} extends {@link FileCollection}.
 * <p>
 * Provides output information of the source set. Allows configuring the default output dirs and specify additional output dirs.
 *
 * <pre class='autoTested'>
 * plugins {
 *     id 'java'
 * }
 *
 * sourceSets {
 *   main {
 *     //if you truly want to override the defaults:
 *     output.resourcesDir = file('out/bin')
 *     // Compiled Java classes should use this directory
 *     java.destinationDirectory.set(file('out/bin'))
 *   }
 * }
 * </pre>
 *
 * Working with generated resources.
 * <p>
 * In general, we recommend generating resources into folders different than the regular resourcesDir and classesDirs.
 * Usually, it makes the build easier to understand and maintain. Also it gives some additional benefits
 * because other Gradle plugins can take advantage of the output dirs 'registered' in the SourceSet.output.
 * For example: Java plugin will use those dirs in calculating class paths and for jarring the content;
 * IDEA and Eclipse plugins will put those folders on relevant classpath.
 * <p>
 * An example how to work with generated resources:
 *
 * <pre class='autoTested'>
 * plugins {
 *   id 'java'
 * }
 *
 * def generateResourcesTask = tasks.register("generate-resources", GenerateResourcesTask) {
 *   resourcesDir.set(layout.buildDirectory.dir("generated-resources/main"))
 * }
 *
 * // Include all outputs of the `generate-resources` task as outputs of the main sourceSet.
 * sourceSets {
 *   main {
 *     output.dir(generateResourcesTask)
 *   }
 * }
 *
 * abstract class GenerateResourcesTask extends DefaultTask {
 *   {@literal @}OutputDirectory
 *   abstract DirectoryProperty getResourcesDir()
 *
 *   {@literal @}TaskAction
 *   def generateResources() {
 *     def generated = resourcesDir.file("myGeneratedResource.properties").get().asFile
 *     generated.text = "message=Stay happy!"
 *   }
 * }
 * </pre>
 *
 * Find more information in {@link #dir(Object)} and {@link #getDirs()}
 */
public interface SourceSetOutput extends FileCollection {

    /**
     * Returns the directories containing compiled classes.
     *
     * @return The classes directories. Never returns null.
     * @since 4.0
     */
    FileCollection getClassesDirs();

    /**
     * Returns the output directory for resources
     * <p>
     * See example at {@link SourceSetOutput}
     *
     * @return The dir resources are copied to.
     */
    @Nullable
    File getResourcesDir();

    /**
     * Sets the output directory for resources
     * <p>
     * See example at {@link SourceSetOutput}
     *
     * @param resourcesDir the classes dir. Should not be null.
     * @since 4.0
     */
    void setResourcesDir(File resourcesDir);

    /**
     * Sets the output directory for resources
     * <p>
     * See example at {@link SourceSetOutput}
     *
     * @param resourcesDir the classes dir. Should not be null.
     */
    void setResourcesDir(Object resourcesDir);

    /**
     * Registers an extra output dir and the builtBy information. Useful for generated resources.
     * <p>
     * See example at {@link SourceSetOutput}
     *
     * @param options - use 'builtBy' key to configure the 'builtBy' task of the dir
     * @param dir - will be resolved as {@link org.gradle.api.Project#file(Object)}
     */
    void dir(Map<String, Object> options, Object dir);

    /**
     * Registers an extra output dir. Useful for generated resources.
     * <p>
     * See example at {@link SourceSetOutput}
     *
     * @param dir - will be resolved as {@link org.gradle.api.Project#file(Object)}
     */
    void dir(Object dir);

    /**
     * Returns all dirs registered with #dir method.
     * Each file is resolved as {@link org.gradle.api.Project#file(Object)}
     * <p>
     * See example at {@link SourceSetOutput}
     *
     * @return a new instance of registered dirs with resolved files
     */
    FileCollection getDirs();

    /**
     * Returns the directories containing generated source files (e.g. by annotation processors during compilation).
     *
     * @return The generated sources directories. Never returns null.
     * @since 5.2
     */
    FileCollection getGeneratedSourcesDirs();
}
