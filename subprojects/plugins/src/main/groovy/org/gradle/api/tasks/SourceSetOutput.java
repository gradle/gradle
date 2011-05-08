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
 * because other gradle plugins can take advantage of the output dirs 'registered' in the SourceSet.output.
 * For example: java plugin will use those dirs in calculating classpaths and for jarring the content;
 * idea and eclipse plugins will put those folders on relevant classpath.
 * <p>
 * An example how to work with generated resources:
 *
 * <pre autoTested=''>
 * apply plugin: 'java'
 *
 * sourceSets {
 *   main {
 *     //let's register an output folder on the main SourceSet:
 *     output.dirs myGeneratedDir: "$buildDir/generated-resources/main"
 *     //it is now a part of the 'main' classpath and will be a part of the jar
 *   }
 * }
 *
 * //a task that generates the resources:
 * task generateMyResources {
 *   doLast {
 *     //notice how the ouptut dir is referred:
 *     def generated = new File(sourceSets.main.output.dirs.myGeneratedDir, "myGeneratedResource.properties")
 *     generated.text = "message=Stay happy!"
 *   }
 * }
 *
 * //if you apply eclipse/idea plugins it might be useful
 * //to have the generated resources ready when eclipse/idea files are built:
 *
 * apply plugin: 'idea'; apply plugin: 'eclipse'
 *
 * eclipseClasspath.dependsOn generateMyResources
 * ideaModule.dependsOn generateMyResources
 * </pre>
 *
 * Find more information in {@link #dirs(java.util.Map)} and {@link #getDirs()}
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
     * Allows to register the output directories.
     * <p>
     * The key of a map entry is a label for a directory - you will use it to refer this dir in the gradle build.
     * <p>
     * The value of a map entry is a directory path resolvable as per {@link org.gradle.api.Project#file(Object)}.
     * <p>
     * Registering the output dir with a SourceSet has benefits because other plugins can use this information
     * (i.e. java plugin will use them in calculating classpath or jarring content;
     * eclipse/idea plugin will put those dirs on the relevant classpath)
     * <p>
     * See example at {@link SourceSetOutput}
     *
     * @param dirs a map containing
     */
    void dirs(Map<String, Object> dirs);

    /**
     * Returns a *new instance* of dirs map with all values resolved to Files.
     * <p>
     * Can be used to refer to registered output dirs. Manipulating this object does not change the SourceSet.output.
     * If you need to register a new output dir please use the {@link #dirs(java.util.Map)} method.
     * <p>
     * See example at {@link SourceSetOutput}
     *
     * @return a new instance  dirs with resolved files
     */
    Map<String, File> getDirs();
}
