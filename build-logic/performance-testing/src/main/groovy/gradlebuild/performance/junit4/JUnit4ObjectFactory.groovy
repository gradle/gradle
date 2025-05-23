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

import jakarta.xml.bind.JAXBElement
import jakarta.xml.bind.annotation.XmlElementDecl
import jakarta.xml.bind.annotation.XmlRegistry

import javax.xml.namespace.QName

/**
 * This object contains factory methods for each
 * Java content interface and Java element interface
 * generated in the gradlebuild.performance.junit4 package.
 * <p>An ObjectFactory allows you to programatically
 * construct new instances of the Java representation
 * for XML content. The Java representation of XML
 * content can consist of schema derived interfaces
 * and classes representing the binding of schema
 * type definitions, element declarations and model
 * groups.  Factory methods for each of these are
 * provided in this class.
 *
 */
@XmlRegistry
public class JUnit4ObjectFactory {

    private final static QName _Skipped_QNAME = new QName("", "skipped");
    private final static QName _SystemErr_QNAME = new QName("", "system-err");
    private final static QName _SystemOut_QNAME = new QName("", "system-out");

    /**
     * Create a new ObjectFactory that can be used to create new instances of schema derived classes for package: gradlebuild.performance.junit4
     *
     */
    public JUnit4ObjectFactory() {
    }

    /**
     * Create an instance of {@link JUnit4Failure }
     *
     */
    public JUnit4Failure createFailure() {
        return new JUnit4Failure();
    }

    /**
     * Create an instance of {@link JUnit4Error }
     *
     */
    public JUnit4Error createError() {
        return new JUnit4Error();
    }

    /**
     * Create an instance of {@link JUnit4Properties }
     *
     */
    public JUnit4Properties createProperties() {
        return new JUnit4Properties();
    }

    /**
     * Create an instance of {@link JUnit4Property }
     *
     */
    public JUnit4Property createProperty() {
        return new JUnit4Property();
    }

    /**
     * Create an instance of {@link JUnit4Testcase }
     *
     */
    public JUnit4Testcase createTestcase() {
        return new JUnit4Testcase();
    }

    /**
     * Create an instance of {@link JUnit4Testsuite }
     *
     */
    public JUnit4Testsuite createTestsuite() {
        return new JUnit4Testsuite();
    }

    /**
     * Create an instance of {@link JUnit4Testsuites }
     *
     */
    public JUnit4Testsuites createTestsuites() {
        return new JUnit4Testsuites();
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}
     *
     * @param value
     *     Java instance representing xml element's value.
     * @return
     *     the new instance of {@link JAXBElement }{@code <}{@link String }{@code >}
     */
    @XmlElementDecl(namespace = "", name = "skipped")
    public JAXBElement<String> createSkipped(String value) {
        return new JAXBElement<String>(_Skipped_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}
     *
     * @param value
     *     Java instance representing xml element's value.
     * @return
     *     the new instance of {@link JAXBElement }{@code <}{@link String }{@code >}
     */
    @XmlElementDecl(namespace = "", name = "system-err")
    public JAXBElement<String> createSystemErr(String value) {
        return new JAXBElement<String>(_SystemErr_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}
     *
     * @param value
     *     Java instance representing xml element's value.
     * @return
     *     the new instance of {@link JAXBElement }{@code <}{@link String }{@code >}
     */
    @XmlElementDecl(namespace = "", name = "system-out")
    public JAXBElement<String> createSystemOut(String value) {
        return new JAXBElement<String>(_SystemOut_QNAME, String.class, null, value);
    }

}
