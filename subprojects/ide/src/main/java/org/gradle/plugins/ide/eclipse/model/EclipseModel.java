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

import com.google.common.base.Preconditions;
import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.model.ObjectFactory;
import org.gradle.internal.xml.XmlTransformer;
import org.gradle.plugins.ide.api.XmlFileContentMerger;

import javax.inject.Inject;
import java.io.File;
import java.util.Map;

import static org.gradle.util.ConfigureUtil.configure;

/**
 * DSL-friendly model of the Eclipse project information.
 * First point of entry for customizing Eclipse project generation.
 *
 * <pre class='autoTested'>
 * apply plugin: 'java'
 * apply plugin: 'eclipse'
 * apply plugin: 'eclipse-wtp' //for web projects only
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
 */
public class EclipseModel {

    private EclipseProject project;

    private EclipseClasspath classpath;

    private EclipseJdt jdt;

    private EclipseWtp wtp;

    /**
     * Injects and returns an instance of {@link ObjectFactory}.
     *
     * @since 4.9
     */
    @Inject
    protected ObjectFactory getObjectFactory() {
        throw new UnsupportedOperationException();
    }

    /**
     * Configures eclipse project information
     * <p>
     * For examples see docs for {@link EclipseProject}
     */
    public EclipseProject getProject() {
        if (project == null) {
            XmlTransformer xmlTransformer = new XmlTransformer();
            xmlTransformer.setIndentation("\t");
            project = getObjectFactory().newInstance(EclipseProject.class, new XmlFileContentMerger(xmlTransformer));
        }
        return project;
    }

    public void setProject(EclipseProject project) {
        this.project = project;
    }

    /**
     * Configures eclipse classpath information
     * <p>
     * For examples see docs for {@link EclipseClasspath}
     */
    public EclipseClasspath getClasspath() {
        return classpath;
    }

    public void setClasspath(EclipseClasspath classpath) {
        this.classpath = classpath;
    }

    /**
     * Configures eclipse java compatibility information (jdt)
     * <p>
     * For examples see docs for {@link EclipseProject}
     */
    public EclipseJdt getJdt() {
        return jdt;
    }

    public void setJdt(EclipseJdt jdt) {
        this.jdt = jdt;
    }

    /**
     * Configures eclipse wtp information
     * <p>
     * For examples see docs for {@link EclipseWtp}
     */
    public EclipseWtp getWtp() {
        if (wtp == null) {
            wtp = getObjectFactory().newInstance(EclipseWtp.class);
        }
        return wtp;
    }

    public void setWtp(EclipseWtp wtp) {
        this.wtp = wtp;
    }

    /**
     * Configures eclipse project information
     * <p>
     * For examples see docs for {@link EclipseProject}
     */
    public void project(Closure closure) {
        configure(closure, getProject());
    }

    /**
     * Configures eclipse project information
     * <p>
     * For examples see docs for {@link EclipseProject}
     *
     * @since 3.5
     */
    public void project(Action<? super EclipseProject> action) {
        action.execute(getProject());
    }

    /**
     * Configures eclipse classpath information
     * <p>
     * For examples see docs for {@link EclipseClasspath}
     */
    public void classpath(Closure closure) {
        configure(closure, classpath);
    }

    /**
     * Configures eclipse classpath information
     * <p>
     * For examples see docs for {@link EclipseClasspath}
     *
     * @since 3.5
     */
    public void classpath(Action<? super EclipseClasspath> action) {
        action.execute(classpath);
    }

    /**
     * Configures eclipse wtp information
     * <p>
     * For examples see docs for {@link EclipseWtp}
     */
    public void wtp(Closure closure) {
        configure(closure, wtp);
    }

    /**
     * Configures eclipse wtp information
     * <p>
     * For examples see docs for {@link EclipseWtp}
     *
     * @since 3.5
     */
    public void wtp(Action<? super EclipseWtp> action) {
        action.execute(wtp);
    }

    /**
     * Configures eclipse java compatibility information (jdt)
     * <p>
     * For examples see docs for {@link EclipseProject}
     */
    public void jdt(Closure closure) {
        configure(closure, getJdt());
    }

    /**
     * Configures eclipse java compatibility information (jdt)
     * <p>
     * For examples see docs for {@link EclipseProject}
     *
     * @since 3.5
     */
    public void jdt(Action<? super EclipseJdt> action) {
        action.execute(getJdt());
    }

    /**
     * Adds path variables to be used for replacing absolute paths in classpath entries.
     * <p>
     * If the beginning of the absolute path of a library or other path-related element matches a value of a variable,
     * a variable entry is used. The matching part of the library path is replaced with the variable name.
     * <p>
     * For example see docs for {@link EclipseModel}
     *
     * @param pathVariables A map with String-&gt;File pairs.
     */
    public void pathVariables(Map<String, File> pathVariables) {
        Preconditions.checkNotNull(pathVariables);
        classpath.getPathVariables().putAll(pathVariables);
        if (wtp != null && wtp.getComponent() != null) {
            wtp.getComponent().getPathVariables().putAll(pathVariables);
        }
    }
}
