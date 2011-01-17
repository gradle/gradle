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
package org.gradle.api.internal.tasks.generator;

import groovy.util.Node;
import groovy.util.XmlParser;
import org.gradle.api.internal.XmlTransformer;
import org.gradle.util.TextUtil;
import org.gradle.util.UncheckedException;

import java.io.*;

/**
 * A {@link PersistableConfigurationObject} which is stored in an XML file.
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

        Writer destination = createWriter(outputStream);
        writeXmlDeclaration(destination);
        xmlTransformer.transform(xml, destination);
    }

    /**
     * Called immediately after the XML file has been read.
     */
    protected abstract void load(Node xml);

    /**
     * Called immediately before the XML file is to be written.
     */
    protected abstract void store(Node xml);

    private Writer createWriter(OutputStream outputStream) {
        try {
            return new OutputStreamWriter(outputStream, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw UncheckedException.asUncheckedException(e);
        }
    }

    private void writeXmlDeclaration(Writer writer) {
        try {
            writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + TextUtil.LINE_SEPARATOR);
        } catch (IOException e) {
            throw UncheckedException.asUncheckedException(e);
        }
    }
}
