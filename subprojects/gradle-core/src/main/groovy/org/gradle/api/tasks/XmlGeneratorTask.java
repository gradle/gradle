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

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.artifacts.maven.XmlProvider;
import org.gradle.api.internal.XmlTransformer;
import org.gradle.api.internal.tasks.generator.PersistableConfigurationObject;
import org.gradle.api.internal.tasks.generator.PersistableConfigurationObjectGenerator;

/**
 * A convenience superclass for those tasks which generate XML configuration files from a domain object of type T.
 *
 * @param <T> The domain object type.
 */
public abstract class XmlGeneratorTask<T extends PersistableConfigurationObject> extends GeneratorTask<T> {
    private final XmlTransformer xmlTransformer = new XmlTransformer();

    public XmlGeneratorTask() {
        generator = new PersistableConfigurationObjectGenerator<T>() {
            public T create() {
                return XmlGeneratorTask.this.create();
            }

            public void configure(T object) {
                XmlGeneratorTask.this.configure(object);
            }
        };
    }

    protected XmlTransformer getXmlTransformer() {
        return xmlTransformer;
    }

    protected abstract void configure(T object);

    protected abstract T create();

    /**
     * Adds a closure to be called when the XML document has been created. The XML is passed to the closure as a
     * parameter in form of a {@link org.gradle.api.artifacts.maven.XmlProvider}. The closure can modify the XML before
     * it is written to the output file.
     *
     * @param closure The closure to execute when the XML has been created.
     */
    public void withXml(Closure closure) {
        xmlTransformer.addAction(closure);
    }

    /**
     * Adds an action to be called when the XML document has been created. The XML is passed to the action as a
     * parameter in form of a {@link org.gradle.api.artifacts.maven.XmlProvider}. The action can modify the XML before
     * it is written to the output file.
     *
     * @param action The action to execute when the IPR XML has been created.
     */
    public void withXml(Action<? super XmlProvider> action) {
        xmlTransformer.addAction(action);
    }
}
