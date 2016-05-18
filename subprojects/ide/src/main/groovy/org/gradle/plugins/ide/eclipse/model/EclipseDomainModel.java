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


import groovy.lang.GroovyObjectSupport;

/**
 * For now, we only need the Project. However this class will contain more domain objects soon.
 */
@Deprecated
public class EclipseDomainModel extends GroovyObjectSupport {

    private Project project;
    private Classpath classpath;

    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = project;
    }

    public Classpath getClasspath() {
        return classpath;
    }

    public void setClasspath(Classpath classpath) {
        this.classpath = classpath;
    }
}
