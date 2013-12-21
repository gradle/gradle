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

package org.gradle.plugins.ide.eclipse.model

import org.gradle.api.JavaVersion
import org.gradle.plugins.ide.api.PropertiesFileContentMerger
import org.gradle.util.ConfigureUtil

/**
 * Enables fine-tuning jdt details of the Eclipse plugin
 *
 * <pre autoTested=''>
 * apply plugin: 'java'
 * apply plugin: 'eclipse'
 *
 * eclipse {
 *   jdt {
 *     //if you want to alter the java versions (by default they are configured with gradle java plugin settings):
 *     sourceCompatibility = 1.6
 *     targetCompatibility = 1.5
 *
 *     file {
 *       //whenMerged closure is the highest voodoo
 *       //and probably should be used only to solve tricky edge cases.
 *       //the type passed to the closure is {@link Jdt}
 *
 *       //closure executed after jdt file content is loaded from existing file
 *       //and after gradle build information is merged
 *       whenMerged { jdt
 *         //you can tinker with the {@link Jdt} here
 *       }
 *       
 *       //withProperties allows addition of properties not currently
 *       //modeled by Gradle
 *       withProperties { properties ->
 *           //you can tinker with the {@link java.util.Properties} here
 *       }
 *     }
 *   }
 * }
 * </pre>
 */
class EclipseJdt {

    /**
     * The source Java language level.
     * <p>
     * For example see docs for {@link EclipseJdt}
     */
    JavaVersion sourceCompatibility

    void setSourceCompatibility(Object sourceCompatibility) {
        this.sourceCompatibility = JavaVersion.toVersion(sourceCompatibility)
    }

    /**
     * The target JVM to generate {@code .class} files for.
     * <p>
     * For example see docs for {@link EclipseJdt}
     */
    JavaVersion targetCompatibility

    void setTargetCompatibility(Object targetCompatibility) {
        this.targetCompatibility = JavaVersion.toVersion(targetCompatibility)
    }

    /**
     * Enables advanced configuration like affecting the way existing jdt file content
     * is merged with gradle build information
     * <p>
     * The object passed to whenMerged{} and beforeMerged{} closures is of type {@link Jdt}
     * <p>
     * The object passed to withProperties{} closures is of type {@link java.util.Properties}
     * <p>
     * For example see docs for {@link EclipseJdt}
     */
    void file(Closure closure) {
        ConfigureUtil.configure(closure, file)
    }

    /**
     * See {@link #file(Closure) }
     */
    final PropertiesFileContentMerger file
    
    EclipseJdt(PropertiesFileContentMerger file) {
        this.file = file
    }
}
