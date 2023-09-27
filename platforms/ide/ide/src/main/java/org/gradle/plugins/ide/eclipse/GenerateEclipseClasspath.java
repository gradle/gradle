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
package org.gradle.plugins.ide.eclipse;

import org.gradle.api.tasks.Internal;
import org.gradle.internal.xml.XmlTransformer;
import org.gradle.plugins.ide.api.XmlGeneratorTask;
import org.gradle.plugins.ide.eclipse.model.Classpath;
import org.gradle.plugins.ide.eclipse.model.EclipseClasspath;
import org.gradle.work.DisableCachingByDefault;

import javax.inject.Inject;

/**
 * Generates an Eclipse <code>.classpath</code> file. If you want to fine tune the eclipse configuration
 * <p>
 * At this moment nearly all configuration is done via {@link EclipseClasspath}.
 */
@DisableCachingByDefault(because = "Not made cacheable, yet")
public abstract class GenerateEclipseClasspath extends XmlGeneratorTask<Classpath> {

    private EclipseClasspath classpath;

    public GenerateEclipseClasspath() {
        getXmlTransformer().setIndentation("\t");
    }

    @Inject
    public GenerateEclipseClasspath(EclipseClasspath classpath) {
        this.classpath = classpath;
    }

    @Override
    protected Classpath create() {
        return new Classpath(getXmlTransformer(), classpath.getFileReferenceFactory());
    }

    @Override
    protected void configure(Classpath xmlClasspath) {
        classpath.mergeXmlClasspath(xmlClasspath);
    }

    @Override
    public XmlTransformer getXmlTransformer() {
        if (classpath == null) {
            return super.getXmlTransformer();
        }
        return classpath.getFile().getXmlTransformer();
    }

    /**
     * The Eclipse Classpath model containing the information required to generate the classpath file.
     */
    @Internal
    public EclipseClasspath getClasspath() {
        return classpath;
    }

    public void setClasspath(EclipseClasspath classpath) {
        this.classpath = classpath;
    }
}
