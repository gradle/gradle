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

package org.gradle.plugins.ide.eclipse.model;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.JavaVersion;
import org.gradle.plugins.ide.api.PropertiesFileContentMerger;
import org.gradle.util.ConfigureUtil;

import javax.inject.Inject;

/**
 * Enables fine-tuning jdt details of the Eclipse plugin
 *
 * <pre class='autoTested'>
 * plugins {
 *     id 'java'
 *     id 'eclipse'
 * }
 *
 * eclipse {
 *   jdt {
 *     //if you want to alter the java versions (by default they are configured with gradle java plugin settings):
 *     sourceCompatibility = 1.6
 *     targetCompatibility = 1.5
 *     javaRuntimeName = "J2SE-1.5"
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
 *       withProperties { properties -&gt;
 *           //you can tinker with the {@link java.util.Properties} here
 *       }
 *     }
 *   }
 * }
 * </pre>
 */
public class EclipseJdt {

    private JavaVersion sourceCompatibility = JavaVersion.current();

    private JavaVersion targetCompatibility = JavaVersion.current();

    private String javaRuntimeName;

    private final PropertiesFileContentMerger file;

    @Inject
    public EclipseJdt(PropertiesFileContentMerger file) {
        this.file = file;
    }

    /**
     * The source Java language level.
     * <p>
     * For example see docs for {@link EclipseJdt}
     */
    public JavaVersion getSourceCompatibility() {
        return sourceCompatibility;
    }

    /**
     * Sets source compatibility.
     *
     * @since 4.0
     */
    public void setSourceCompatibility(JavaVersion sourceCompatibility) {
        setSourceCompatibility((Object) sourceCompatibility);
    }

    public void setSourceCompatibility(Object sourceCompatibility) {
        JavaVersion version = JavaVersion.toVersion(sourceCompatibility);
        if (version != null) {
            this.sourceCompatibility = version;
        }
    }

    /**
     * The target JVM to generate {@code .class} files for.
     * <p>
     * For example see docs for {@link EclipseJdt}
     */
    public JavaVersion getTargetCompatibility() {
        return targetCompatibility;
    }

    /**
     * Sets target compatibility.
     *
     * @since 4.0
     */
    public void setTargetCompatibility(JavaVersion targetCompatibility) {
        setTargetCompatibility((Object) targetCompatibility);
    }

    public void setTargetCompatibility(Object targetCompatibility) {
        JavaVersion version = JavaVersion.toVersion(targetCompatibility);
        if (version != null) {
            this.targetCompatibility = version;
        }
    }

    /**
     * The name of the Java Runtime to use.
     * <p>
     * For example see docs for {@link EclipseJdt}
     */
    public String getJavaRuntimeName() {
        return javaRuntimeName;
    }

    public void setJavaRuntimeName(String javaRuntimeName) {
        this.javaRuntimeName = javaRuntimeName;
    }

    /**
     * See {@link #file(Action) }
     */
    public PropertiesFileContentMerger getFile() {
        return file;
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
    public void file(Closure closure) {
        ConfigureUtil.configure(closure, file);
    }

    /**
     * Enables advanced configuration like affecting the way existing jdt file content
     * is merged with gradle build information
     * <p>
     * The object passed to whenMerged{} and beforeMerged{} actions is of type {@link Jdt}
     * <p>
     * The object passed to withProperties{} actions is of type {@link java.util.Properties}
     * <p>
     * For example see docs for {@link EclipseJdt}
     *
     * @since 3.5
     */
    public void file(Action<? super PropertiesFileContentMerger> action) {
        action.execute(file);
    }
}
