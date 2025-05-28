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

package gradlebuild.performance.junit4

import jakarta.xml.bind.JAXBContext
import jakarta.xml.bind.Unmarshaller

import javax.xml.stream.XMLInputFactory
import javax.xml.transform.stream.StreamSource

/**
 * Utility for securely parsing the JUnit4 XML reports.
 * https://cheatsheetseries.owasp.org/cheatsheets/XML_External_Entity_Prevention_Cheat_Sheet.html#jaxb-unmarshaller
 */
class SecureUnmarshaller {
    private final JAXBContext context = JAXBContext.newInstance(JUnit4Testsuites.class)
    private final XMLInputFactory xif = XMLInputFactory.newFactory()
    private final Unmarshaller unmarshaller = context.createUnmarshaller()

    SecureUnmarshaller() {
        xif.setProperty(XMLInputFactory.SUPPORT_DTD, false)
        xif.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
    }

    List<JUnit4Testsuite> unmarshal(File xml) {
        def maybeTestsuites = unmarshaller.unmarshal(xif.createXMLStreamReader(new StreamSource(xml)))
        if (maybeTestsuites instanceof JUnit4Testsuites) {
            return maybeTestsuites.testsuite
        }
        return [maybeTestsuites] as List<JUnit4Testsuite>
    }
}
