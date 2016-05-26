/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.testing.jacoco.tasks;

import groovy.lang.GroovyObject;
import groovy.lang.MetaClass;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.gradle.api.DefaultTask;
import org.gradle.api.Incubating;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.InputFiles;

/**
 * Base class for Jacoco tasks.
 */
@Incubating
public abstract class JacocoBase extends DefaultTask implements GroovyObject {

    // ----- backwards compatibility section, implements the GroovyObject interface
    private transient MetaClass metaClass;

    public JacocoBase() {
        this.metaClass = InvokerHelper.getMetaClass(this.getClass());
    }

    public Object getProperty(String property) {
        return getMetaClass().getProperty(this, property);
    }

    public Object invokeMethod(String name, Object args) {
        return getMetaClass().invokeMethod(this, name, args);
    }

    public MetaClass getMetaClass() {
        if (metaClass == null) {
            metaClass = InvokerHelper.getMetaClass(getClass());
        }
        return metaClass;
    }

    public void setMetaClass(MetaClass metaClass) {
        this.metaClass = metaClass;
    }
    // ------- end of backwards compatibility section

    private FileCollection jacocoClasspath;

    /**
     * Classpath containing Jacoco classes for use by the task.
     */
    @InputFiles
    public FileCollection getJacocoClasspath() {
        return jacocoClasspath;
    }

    public void setJacocoClasspath(FileCollection jacocoClasspath) {
        this.jacocoClasspath = jacocoClasspath;
    }
}
