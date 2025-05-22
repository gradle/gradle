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

import jakarta.xml.bind.annotation.XmlAccessType
import jakarta.xml.bind.annotation.XmlAccessorType
import jakarta.xml.bind.annotation.XmlAttribute
import jakarta.xml.bind.annotation.XmlElement
import jakarta.xml.bind.annotation.XmlRootElement
import jakarta.xml.bind.annotation.XmlType

/**
 * <p>Java class for anonymous complex type.
 *
 * <p>The following schema fragment specifies the expected content contained within this class.
 *
 * <pre>
 * &lt;complexType&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element ref="{}properties" minOccurs="0"/&gt;
 *         &lt;element ref="{}testcase" maxOccurs="unbounded" minOccurs="0"/&gt;
 *         &lt;element ref="{}system-out" minOccurs="0"/&gt;
 *         &lt;element ref="{}system-err" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *       &lt;attribute name="name" use="required" type="{http://www.w3.org/2001/XMLSchema}string" /&gt;
 *       &lt;attribute name="tests" use="required" type="{http://www.w3.org/2001/XMLSchema}string" /&gt;
 *       &lt;attribute name="failures" type="{http://www.w3.org/2001/XMLSchema}string" /&gt;
 *       &lt;attribute name="errors" type="{http://www.w3.org/2001/XMLSchema}string" /&gt;
 *       &lt;attribute name="time" type="{http://www.w3.org/2001/XMLSchema}string" /&gt;
 *       &lt;attribute name="disabled" type="{http://www.w3.org/2001/XMLSchema}string" /&gt;
 *       &lt;attribute name="skipped" type="{http://www.w3.org/2001/XMLSchema}string" /&gt;
 *       &lt;attribute name="timestamp" type="{http://www.w3.org/2001/XMLSchema}string" /&gt;
 *       &lt;attribute name="hostname" type="{http://www.w3.org/2001/XMLSchema}string" /&gt;
 *       &lt;attribute name="id" type="{http://www.w3.org/2001/XMLSchema}string" /&gt;
 *       &lt;attribute name="package" type="{http://www.w3.org/2001/XMLSchema}string" /&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 *
 *
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = [
    "properties",
    "testcase",
    "systemOut",
    "systemErr",
])
@XmlRootElement(name = "testsuite")
public class JUnit4Testsuite {

    protected JUnit4Properties properties;
    protected List<JUnit4Testcase> testcase;
    @XmlElement(name = "system-out")
    protected String systemOut;
    @XmlElement(name = "system-err")
    protected String systemErr;
    @XmlAttribute(name = "name", required = true)
    protected String name;
    @XmlAttribute(name = "tests", required = true)
    protected String tests;
    @XmlAttribute(name = "failures")
    protected String failures;
    @XmlAttribute(name = "errors")
    protected String errors;
    @XmlAttribute(name = "time")
    protected String time;
    @XmlAttribute(name = "disabled")
    protected String disabled;
    @XmlAttribute(name = "skipped")
    protected String skipped;
    @XmlAttribute(name = "timestamp")
    protected String timestamp;
    @XmlAttribute(name = "hostname")
    protected String hostname;
    @XmlAttribute(name = "id")
    protected String id;
    @XmlAttribute(name = "package")
    protected String _package;

    /**
     * Gets the value of the properties property.
     *
     * @return
     *     possible object is
     *     {@link JUnit4Properties }
     *
     */
    public JUnit4Properties getProperties() {
        return properties;
    }

    /**
     * Sets the value of the properties property.
     *
     * @param value
     *     allowed object is
     *     {@link JUnit4Properties }
     *
     */
    public void setProperties(JUnit4Properties value) {
        this.properties = value;
    }

    /**
     * Gets the value of the testcase property.
     *
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the Jakarta XML Binding object.
     * This is why there is not a <CODE>set</CODE> method for the testcase property.
     *
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getTestcase().add(newItem);
     * </pre>
     *
     *
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link JUnit4Testcase }
     *
     *
     */
    public List<JUnit4Testcase> getTestcase() {
        if (testcase == null) {
            testcase = new ArrayList<JUnit4Testcase>();
        }
        return this.testcase;
    }

    /**
     * Gets the value of the systemOut property.
     *
     * @return
     *     possible object is
     *     {@link String }
     *
     */
    public String getSystemOut() {
        return systemOut;
    }

    /**
     * Sets the value of the systemOut property.
     *
     * @param value
     *     allowed object is
     *     {@link String }
     *
     */
    public void setSystemOut(String value) {
        this.systemOut = value;
    }

    /**
     * Gets the value of the systemErr property.
     *
     * @return
     *     possible object is
     *     {@link String }
     *
     */
    public String getSystemErr() {
        return systemErr;
    }

    /**
     * Sets the value of the systemErr property.
     *
     * @param value
     *     allowed object is
     *     {@link String }
     *
     */
    public void setSystemErr(String value) {
        this.systemErr = value;
    }

    /**
     * Gets the value of the name property.
     *
     * @return
     *     possible object is
     *     {@link String }
     *
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the value of the name property.
     *
     * @param value
     *     allowed object is
     *     {@link String }
     *
     */
    public void setName(String value) {
        this.name = value;
    }

    /**
     * Gets the value of the tests property.
     *
     * @return
     *     possible object is
     *     {@link String }
     *
     */
    public String getTests() {
        return tests;
    }

    /**
     * Sets the value of the tests property.
     *
     * @param value
     *     allowed object is
     *     {@link String }
     *
     */
    public void setTests(String value) {
        this.tests = value;
    }

    /**
     * Gets the value of the failures property.
     *
     * @return
     *     possible object is
     *     {@link String }
     *
     */
    public String getFailures() {
        return failures;
    }

    /**
     * Sets the value of the failures property.
     *
     * @param value
     *     allowed object is
     *     {@link String }
     *
     */
    public void setFailures(String value) {
        this.failures = value;
    }

    /**
     * Gets the value of the errors property.
     *
     * @return
     *     possible object is
     *     {@link String }
     *
     */
    public String getErrors() {
        return errors;
    }

    /**
     * Sets the value of the errors property.
     *
     * @param value
     *     allowed object is
     *     {@link String }
     *
     */
    public void setErrors(String value) {
        this.errors = value;
    }

    /**
     * Gets the value of the time property.
     *
     * @return
     *     possible object is
     *     {@link String }
     *
     */
    public String getTime() {
        return time;
    }

    /**
     * Sets the value of the time property.
     *
     * @param value
     *     allowed object is
     *     {@link String }
     *
     */
    public void setTime(String value) {
        this.time = value;
    }

    /**
     * Gets the value of the disabled property.
     *
     * @return
     *     possible object is
     *     {@link String }
     *
     */
    public String getDisabled() {
        return disabled;
    }

    /**
     * Sets the value of the disabled property.
     *
     * @param value
     *     allowed object is
     *     {@link String }
     *
     */
    public void setDisabled(String value) {
        this.disabled = value;
    }

    /**
     * Gets the value of the skipped property.
     *
     * @return
     *     possible object is
     *     {@link String }
     *
     */
    public String getSkipped() {
        return skipped;
    }

    /**
     * Sets the value of the skipped property.
     *
     * @param value
     *     allowed object is
     *     {@link String }
     *
     */
    public void setSkipped(String value) {
        this.skipped = value;
    }

    /**
     * Gets the value of the timestamp property.
     *
     * @return
     *     possible object is
     *     {@link String }
     *
     */
    public String getTimestamp() {
        return timestamp;
    }

    /**
     * Sets the value of the timestamp property.
     *
     * @param value
     *     allowed object is
     *     {@link String }
     *
     */
    public void setTimestamp(String value) {
        this.timestamp = value;
    }

    /**
     * Gets the value of the hostname property.
     *
     * @return
     *     possible object is
     *     {@link String }
     *
     */
    public String getHostname() {
        return hostname;
    }

    /**
     * Sets the value of the hostname property.
     *
     * @param value
     *     allowed object is
     *     {@link String }
     *
     */
    public void setHostname(String value) {
        this.hostname = value;
    }

    /**
     * Gets the value of the id property.
     *
     * @return
     *     possible object is
     *     {@link String }
     *
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the value of the id property.
     *
     * @param value
     *     allowed object is
     *     {@link String }
     *
     */
    public void setId(String value) {
        this.id = value;
    }

    /**
     * Gets the value of the package property.
     *
     * @return
     *     possible object is
     *     {@link String }
     *
     */
    public String getPackage() {
        return _package;
    }

    /**
     * Sets the value of the package property.
     *
     * @param value
     *     allowed object is
     *     {@link String }
     *
     */
    public void setPackage(String value) {
        this._package = value;
    }

}
