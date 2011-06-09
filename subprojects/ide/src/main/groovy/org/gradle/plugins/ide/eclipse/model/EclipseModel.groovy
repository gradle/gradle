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
package org.gradle.plugins.ide.eclipse.model

import org.gradle.util.ConfigureUtil

/**
 * DSL-friendly model of the Eclipse project information.
 * First point of entry when it comes to customizing the eclipse generation
 *
 * <pre autoTested=''>
 * apply plugin: 'java'
 * apply plugin: 'war'  //needed for wtp
 * apply plugin: 'eclipse'
 *
 * eclipse {
 *   pathVariables 'GRADLE_HOME': file('/best/software/gradle'), 'TOMCAT_HOME': file('../tomcat')
 *
 *   project {
 *     //see docs for {@link EclipseProject}
 *   }
 *
 *   classpath {
 *     //see docs for {@link EclipseClasspath}
 *   }
 *
 *   wtp {
 *     //see docs for {@link EclipseWtp}
 *   }
 * }
 * </pre>
 *
 * More examples in docs for {@link EclipseProject}, {@link EclipseClasspath}, {@link EclipseWtp}
 *
 * @author: Szczepan Faber, created at: 4/13/11
 */
class EclipseModel {

    EclipseProject project
    EclipseClasspath classpath
    EclipseJdt jdt

    EclipseWtp wtp = new EclipseWtp()

    EclipseModel eclipse = this
    void eclipse(Closure closure) {
        ConfigureUtil.configure(closure, this)
    }

    /**
     * Configures eclipse project
     * <p>
     * For examples see docs for {@link EclipseProject}
     *
     * @param closure
     */
    void project(Closure closure) {
        ConfigureUtil.configure(closure, project)
    }

    /**
     * Configures eclipse classpath information
     * <p>
     * For examples see docs for {@link EclipseClasspath}
     *
     * @param closure
     */
    void classpath(Closure closure) {
        ConfigureUtil.configure(closure, classpath)
    }

    /**
     * Configures eclipse wtp information
     * <p>
     * For examples see docs for {@link EclipseWtp}
     *
     * @param closure
     */
    void wtp(Closure closure) {
        ConfigureUtil.configure(closure, wtp)
    }

    /**
     * Configures eclipse java compatibility information (jdt)
     * <p>
     * For examples see docs for {@link EclipseProject}
     *
     * @param closure
     */
    void jdt(Closure closure) {
        ConfigureUtil.configure(closure, jdt)
    }

    /**
     * Adds path variables to be used for replacing absolute paths in classpath entries.
     * <p>
     * For example see docs for {@link EclipseModel}
     *
     * @param pathVariables A map with String->File pairs.
     */
    void pathVariables(Map<String, File> pathVariables) {
        assert pathVariables != null
        classpath.pathVariables.putAll pathVariables
        if (wtp.component) {
            wtp.component.pathVariables.putAll pathVariables
        }
    }
}
