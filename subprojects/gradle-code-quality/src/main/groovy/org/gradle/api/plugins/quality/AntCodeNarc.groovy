/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.plugins.quality

import groovy.xml.Namespace
import javax.xml.XMLConstants
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.SchemaFactory
import org.apache.log4j.Logger
import org.apache.tools.ant.BuildException
import org.codenarc.ant.CodeNarcTask
import org.codenarc.ruleset.FilteredRuleSet
import org.codenarc.ruleset.GroovyDslRuleSet
import org.codenarc.ruleset.RuleSet
import org.codenarc.ruleset.RuleSetUtil
import org.codenarc.util.PropertyUtil
import org.codenarc.util.io.ClassPathResource
import org.codenarc.util.io.DefaultResourceFactory
import org.codenarc.util.io.ResourceFactory
import org.gradle.api.AntBuilder
import org.gradle.api.GradleException
import org.gradle.api.file.FileCollection

class AntCodeNarc {
    def execute(AntBuilder ant, FileCollection source, File configFile, File reportFile, boolean ignoreFailures) {
        ant.project.addTaskDefinition('codenarc', CodeNarcTask)

        // Patch for ClassLoader problems in CodeNarc 0.7
        RuleSetUtil.metaClass.static.loadRuleSetFile = { String path ->
            RuleSetUtil.isXmlFile(path) ? new XmlFileRuleSet(path) : new GroovyDslRuleSet(path)
        }

        try {
            ant.codenarc(ruleSetFiles: "file:$configFile", maxPriority1Violations: 0, maxPriority2Violations: 0, maxPriority3Violations: 0) {
                report(type: 'html', toFile: reportFile)
                source.addToAntBuilder(ant, 'fileset', FileCollection.AntType.FileSet)
            }
        } catch (BuildException e) {
            if (e.message.matches('Exceeded maximum number of priority \\d* violations.*')) {
                if (ignoreFailures) {
                    return
                }
                throw new GradleException("CodeNarc check violations were found in $source. See the report at $reportFile.", e)
            }
            throw e
        }
    }
}

/**
 * THIS CLASS HAS BEEN COPIED FROM CODENARC AND PATCHED
 *
 * A <code>RuleSet</code> implementation that parses Rule definitions from XML read from a
 * file. The filename passed into the constructor is interpreted relative to the classpath, by
 * default, but may be optionally prefixed by any of the valid java.net.URL prefixes, such as
 * "file:" (to load from a relative or absolute path on the filesystem), or "http:".
 * <p/>
 * Note that this class attempts to read the file and parse the XML from within the constructor.
 *
 * @author Chris Mair
 * @version $Revision$ - $Date$
 */class XmlFileRuleSet implements RuleSet {
    private static final LOG = Logger.getLogger(XmlFileRuleSet)
    private ResourceFactory resourceFactory = new DefaultResourceFactory()
    private List rules = []

    /**
     * Construct a new instance on the specified RuleSet file path
     * @param path - the path to the XML RuleSet definition file. The path is relative to the classpath,
     *      by default, but may be optionally prefixed by any of the valid java.net.URL prefixes, such
     *      as "file:" (to load from a relative or absolute path on the filesystem), or "http:". The
     *      path must not be empty or null.
     */
    XmlFileRuleSet(String path) {
        assert path
        LOG.info("Loading ruleset from [$path]")
        def inputStream = resourceFactory.getResource(path).inputStream
        inputStream.withReader {reader ->
            def xmlReaderRuleSet = new XmlReaderRuleSet(reader)
            this.rules = xmlReaderRuleSet.rules
        }
    }

    /**
     * @return a List of Rule objects
     */
    List getRules() {
        return rules
    }
}

/**
 * THIS CLASS HAS BEEN COPIED FROM CODENARC AND PATCHED
 *
 * A <code>RuleSet</code> implementation that parses Rule definitions from XML read from a
 * <code>Reader</code>. Note that this class attempts to read and parse the XML from within
 * the constructor.
 *
 * @author Chris Mair
 * @version $Revision$ - $Date$
 */
class XmlReaderRuleSet implements RuleSet {

    private static final LOG = Logger.getLogger(XmlReaderRuleSet)
    private static final NS = new Namespace('http://codenarc.org/ruleset/1.0')
    private static final RULESET_SCHEMA_FILE = 'ruleset-schema.xsd'
    private List rules = []

    /**
     * Construct a new instance on the specified Reader
     * @param reader - the Reader from which the XML will be read; must not be null
     */
    XmlReaderRuleSet(Reader reader) {
        assert reader
        def xml = reader.text
        validateXml(xml)

        def ruleset = new XmlParser().parseText(xml)
        loadRuleSetRefElements(ruleset)
        loadRuleElements(ruleset)
        loadRuleScriptElements(ruleset)
        rules = rules.asImmutable()
    }

    /**
     * @return a List of Rule objects
     */
    List getRules() {
        return rules
    }

    //--------------------------------------------------------------------------
    // Internal Helper Methods
    //--------------------------------------------------------------------------

    private void loadRuleSetRefElements(ruleset) {
        ruleset[NS.'ruleset-ref'].each { ruleSetRefNode ->
            def ruleSetPath = ruleSetRefNode.attribute('path')
            def refRuleSet = RuleSetUtil.loadRuleSetFile(ruleSetPath)
            def allRules = refRuleSet.rules
            def filteredRuleSet = new FilteredRuleSet(refRuleSet)
            ruleSetRefNode[NS.'include'].each { includeNode ->
                def includeRuleName = includeNode.attribute('name')
                filteredRuleSet.addInclude(includeRuleName)
            }
            ruleSetRefNode[NS.'exclude'].each { excludeNode ->
                def excludeRuleName = excludeNode.attribute('name')
                filteredRuleSet.addExclude(excludeRuleName)
            }
            ruleSetRefNode[NS.'rule-config'].each { configNode ->
                def configRuleName = configNode.attribute('name')
                def rule = allRules.find { it.name == configRuleName }
                assert rule, "Rule named [$configRuleName] referenced within <rule-config> was not found"
                configNode[NS.property].each { p ->
                    def name = p.attribute('name')
                    def value = p.attribute('value')
                    PropertyUtil.setPropertyFromString(rule, name, value)
                }
            }
            rules.addAll(filteredRuleSet.rules)
        }
    }

    private void loadRuleElements(ruleset) {
        ruleset[NS.rule].each { ruleNode ->
            def ruleClassName = ruleNode.attribute('class')
            def ruleClass = getClass().classLoader.loadClass(ruleClassName.toString())
            RuleSetUtil.assertClassImplementsRuleInterface(ruleClass)
            def rule = ruleClass.newInstance()
            rules << rule
            setRuleProperties(ruleNode, rule)
        }
    }

    private void loadRuleScriptElements(ruleset) {
        ruleset[NS.'rule-script'].each { ruleScriptNode ->
            def ruleScriptPath = ruleScriptNode.attribute('path')
            def rule = RuleSetUtil.loadRuleScriptFile(ruleScriptPath)
            rules << rule
            setRuleProperties(ruleScriptNode, rule)
        }
    }

    private def setRuleProperties(ruleNode, rule) {
        ruleNode[NS.property].each {p ->
            def name = p.attribute('name')
            def value = p.attribute('value')
            PropertyUtil.setPropertyFromString(rule, name, value)
        }
    }

    private void validateXml(String xml) {
        def factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
        def schema = factory.newSchema(new StreamSource(getSchemaXmlInputStream()))
        def validator = schema.newValidator()
        validator.validate(new StreamSource(new StringReader(xml)))
    }

    private InputStream getSchemaXmlInputStream() {
        return ClassPathResource.getInputStream(RULESET_SCHEMA_FILE)
    }
}