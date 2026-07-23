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

import org.gradle.api.JavaVersion;

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
 *   }
 * }
 * </pre>
 */
public abstract class EclipseJdt {

    private JavaVersion sourceCompatibility = JavaVersion.current();

    private JavaVersion targetCompatibility = JavaVersion.current();

    private String javaRuntimeName;

    @Inject
    public EclipseJdt() {
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

    /**
     * Set Java Runtime name.
     */
    public void setJavaRuntimeName(String javaRuntimeName) {
        this.javaRuntimeName = javaRuntimeName;
    }

}
