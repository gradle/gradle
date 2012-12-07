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
package org.gradle.plugins.ide.internal.generator;


import org.gradle.api.internal.xml.XmlTransformer

/**
 * A {@link org.gradle.plugins.ide.internal.generator.generator.PersistableConfigurationObject} which is stored in an XML file.
 */
public abstract class XmlPersistableConfigurationObject extends AbstractPersistableConfigurationObject {
    private final XmlTransformer xmlTransformer;
    private Node xml;

    protected XmlPersistableConfigurationObject(XmlTransformer xmlTransformer) {
        this.xmlTransformer = xmlTransformer;
    }

    public Node getXml() {
        return xml;
    }

    @Override
    public void load(InputStream inputStream) throws Exception {
        xml = new XmlParser().parse(inputStream);
        load(xml);
    }

    @Override
    public void store(OutputStream outputStream) {
        store(xml);
        xmlTransformer.transform(xml, outputStream);
    }

    /**
     * Called immediately after the XML file has been read.
     */
    protected void load(Node xml) {
        // no-op
    }

    /**
     * Called immediately before the XML file is to be written.
     */
    protected void store(Node xml) {
        // no-op
    }

    public void transformAction(Closure action) {
        xmlTransformer.addAction(action)
    }
}
