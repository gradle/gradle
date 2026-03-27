/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.testing.testengine.util;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TestFileParser {
    public boolean isValidTestDefinitionFile(File file) {
        return file.exists() && file.isFile() && file.canRead()
                && (file.getName().toLowerCase().endsWith(".xml") || file.getName().toLowerCase().endsWith(".rbt"));
    }

    /**
     * Parses the given test definitions XML file and extracts the names of the tests defined within it.
     * <p>
     * The given file should be a valid definition file according to {@link #isValidTestDefinitionFile(File)}.
     *
     * @param testDefinitionsFile The XML file containing test definitions.
     * @return A list of test names extracted from the file. If no tests are found, returns an empty list.
     * @throws RuntimeException if there is an error parsing the XML file.
     */
    public List<String> parseTestNames(File testDefinitionsFile) {
        List<String> names = new ArrayList<>();

        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(testDefinitionsFile);
            doc.getDocumentElement().normalize();

            NodeList nodeList = doc.getElementsByTagName("test");

            for (int i = 0; i < nodeList.getLength(); i++) {
                Element testElement = (Element) nodeList.item(i);
                String name = testElement.getAttribute("name");
                names.add(name);
            }
        } catch (Exception e) {
            return Collections.emptyList();
        }

        return names;
    }
}
