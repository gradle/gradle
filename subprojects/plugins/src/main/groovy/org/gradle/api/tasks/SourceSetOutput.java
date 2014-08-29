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

import java.io.File;
import java.util.Map;

/**
 * A collection of all output directories (compiled classes, processed resources, etc.) - notice that {@link SourceSetOutput} extends {@link FileCollection}.
 * <p>
 * Provides output information of the source set. Allows configuring the default output dirs and specify additional output dirs.
 *
 * <pre autoTested=''>
 * apply plugin: 'java'
 *
 * sourceSets {
 *   main {
 *     //if you truly want to override the defaults:
 *     output.resourcesDir = 'out/res'
 *     output.classesDir   = 'out/bin'
 *   }
 * }
 * </pre>
 *
 * Working with generated resources.
 * <p>
 * In general, we recommend generating resources into folders different than the regular resourcesDir and classesDir.
 * Usually, it makes the build easier to understand and maintain. Also it gives some additional benefits
 * because other Gradle plugins can take advantage of the output dirs 'registered' in the SourceSet.output.
 * For example: Java plugin will use those dirs in calculating class paths and for jarring the content;
 * IDEA and Eclipse plugins will put those folders on relevant classpath.
 * <p>
 * An example how to work with generated resources:
 *
 * <pre autoTested=''>
 * apply plugin: 'java'
 *
 * def generatedResources = "$buildDir/generated-resources/main"
 *
 * sourceSets {
 *   main {
 *     //let's register an output folder on the main SourceSet:
 *     output.dir(generatedResources, builtBy: 'generateMyResources')
 *     //it is now a part of the 'main' classpath and will be a part of the jar
 *   }
 * }
 *
 * //a task that generates the resources:
 * task generateMyResources {
 *   doLast {
 *     def generated = new File(generatedResources, "myGeneratedResource.properties")
 *     generated.text = "message=Stay happy!"
 *   }
 * }
 *
 * //Java plugin task 'classes' and 'testClasses' will automatically depend on relevant tasks registered with 'builtBy'
 *
 * //Eclipse/IDEA plugins will automatically depend on 'generateMyResources'
 * //because the output dir was registered with 'builtBy' information
 * apply plugin: 'idea'; apply plugin: 'eclipse'
 * </pre>
 *
 * Find more information in {@link #dir(java.util.Map, Object)} and {@link #getDirs()}
 */
public interface SourceSetOutput extends FileCollection {

    /**
     * Returns the directory to assemble the compiled classes into.
     * <p>
     * See example at {@link SourceSetOutput}
     *
     * @return The classes dir. Never returns null.
     */
    File getClassesDir();

    /**
     * Sets the directory to assemble the compiled classes into.
     * <p>
     * See example at {@link SourceSetOutput}
     *
     * @param classesDir the classes dir. Should not be null.
     */
    void setClassesDir(Object classesDir);

    /**
     * Returns the output directory for resources
     * <p>
     * See example at {@link SourceSetOutput}
     *
     * @return The dir resources are copied to.
     */
    File getResourcesDir();

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
}
