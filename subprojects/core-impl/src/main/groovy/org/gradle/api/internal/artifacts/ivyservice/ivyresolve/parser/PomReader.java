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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser;

import org.apache.ivy.core.IvyPatternHelper;
import org.apache.ivy.core.module.descriptor.License;
import org.apache.ivy.core.module.id.ModuleId;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.util.XMLHelper;
import org.gradle.api.Transformer;
import org.gradle.api.internal.artifacts.ivyservice.IvyUtil;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.data.MavenDependencyKey;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.data.PomDependencyMgt;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.data.PomProfile;
import org.gradle.api.internal.externalresource.LocallyAvailableExternalResource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.parsers.DocumentBuilder;
import java.io.*;
import java.util.*;


/**
 * Copied from org.apache.ivy.plugins.parser.m2.PomReader.
 */
public class PomReader implements PomParent {

    private static final String PACKAGING = "packaging";
    private static final String DEPENDENCY = "dependency";
    private static final String DEPENDENCIES = "dependencies";
    private static final String DEPENDENCY_MGT = "dependencyManagement";
    private static final String PROJECT = "project";
    private static final String MODEL = "model";
    private static final String GROUP_ID = "groupId";
    private static final String ARTIFACT_ID = "artifactId";
    private static final String VERSION = "version";
    private static final String DESCRIPTION = "description";
    private static final String HOMEPAGE = "url";
    private static final String LICENSES = "licenses";
    private static final String LICENSE = "license";
    private static final String LICENSE_NAME = "name";
    private static final String LICENSE_URL = "url";
    private static final String PARENT = "parent";
    private static final String SCOPE = "scope";
    private static final String CLASSIFIER = "classifier";
    private static final String OPTIONAL = "optional";
    private static final String EXCLUSIONS = "exclusions";
    private static final String EXCLUSION = "exclusion";
    private static final String DISTRIBUTION_MGT = "distributionManagement";
    private static final String RELOCATION = "relocation";
    private static final String PROPERTIES = "properties";
    private static final String TYPE = "type";
    private static final String PROFILES = "profiles";
    private static final String PROFILE = "profile";
    private static final String PROFILE_ID = "id";
    private static final String PROFILE_ACTIVATION = "activation";
    private static final String PROFILE_ACTIVATION_ACTIVE_BY_DEFAULT = "activeByDefault";

    private PomParent pomParent = new RootPomParent();
    private final Map<String, String> properties = new HashMap<String, String>();
    private List<PomDependencyMgt> declaredDependencyMgts;
    private List<PomProfile> declaredActivePomProfiles;
    private Map<MavenDependencyKey, PomDependencyMgt> resolvedDependencyMgts;
    private final Map<MavenDependencyKey, PomDependencyMgt> importedDependencyMgts = new LinkedHashMap<MavenDependencyKey, PomDependencyMgt>();
    private Map<MavenDependencyKey, PomDependencyData> resolvedDependencies;

    private final Element projectElement;
    private final Element parentElement;

    public PomReader(final LocallyAvailableExternalResource resource) throws IOException, SAXException {
        final String systemId = resource.getLocalResource().getFile().toURI().toASCIIString();
        Document pomDomDoc = resource.withContent(new Transformer<Document, InputStream>() {
            public Document transform(InputStream inputStream) {
                try {
                    return parseToDom(inputStream, systemId);
                } catch (Exception e) {
                    throw new MetaDataParseException("POM", resource, e);
                }
            }
        });
        projectElement = pomDomDoc.getDocumentElement();
        if (!PROJECT.equals(projectElement.getNodeName()) && !MODEL.equals(projectElement.getNodeName())) {
            throw new SAXParseException("project must be the root tag", systemId, systemId, 0, 0);
        }
        parentElement = getFirstChildElement(projectElement, PARENT);

        setDefaultParentGavProperties();
        setPomProperties();
        setActiveProfileProperties();
    }

    public void setPomParent(PomParent pomParent) {
        this.pomParent = pomParent;
        setPomParentProperties();
    }

    private void setPomParentProperties() {
        Map<String, String> parentPomProps = pomParent.getProperties();

        for(Map.Entry<String, String> entry : parentPomProps.entrySet()) {
            setProperty(entry.getKey(), entry.getValue());
        }
    }

    private void setPomProperties() {
        for(Map.Entry<String, String> pomProperty : getPomProperties().entrySet()) {
            setProperty(pomProperty.getKey(), pomProperty.getValue());
        }
    }

    /**
     * Sets properties for all active profiles. Properties from an active profile override existing POM properties.
     */
    private void setActiveProfileProperties() {
        for(PomProfile activePomProfile : parseActivePomProfiles()) {
            for(Map.Entry<String, String> property : activePomProfile.getProperties().entrySet()) {
                properties.put(property.getKey(), property.getValue());
            }
        }
    }

    private void setDefaultParentGavProperties() {
        setGavPropertyValueWithoutReplacement(GavProperty.PARENT_GROUP_ID, getParentGroupId());
        setGavPropertyValueWithoutReplacement(GavProperty.PARENT_VERSION, getParentVersion());
    }

    private void setGavPropertyValueWithoutReplacement(GavProperty gavProperty, String propertyValue) {
        for(String name : gavProperty.getNames()) {
            setProperty(name, propertyValue);
        }
    }

    private enum GavProperty {
        PARENT_VERSION("parent.version", "project.parent.version"),
        PARENT_GROUP_ID("parent.groupId", "project.parent.groupId"),
        GROUP_ID("project.groupId", "pom.groupId", "groupId"),
        ARTIFACT_ID("project.artifactId", "pom.artifactId", "artifactId"),
        VERSION("project.version", "pom.version", "version");

        private final String[] names;

        private GavProperty(String... names) {
            this.names = names;
        }

        public String[] getNames() {
            return names;
        }
    }

    @Override
    public String toString() {
        return projectElement.getOwnerDocument().getDocumentURI();
    }

    public static Document parseToDom(InputStream stream, String systemId) throws IOException, SAXException {
        EntityResolver entityResolver = new EntityResolver() {
            public InputSource resolveEntity(String publicId, String systemId)
                    throws SAXException, IOException {
                if ((systemId != null) && systemId.endsWith("m2-entities.ent")) {
                    return new InputSource(org.apache.ivy.plugins.parser.m2.PomReader.class.getResourceAsStream("m2-entities.ent"));
                }
                return null;
            }
        };
        InputStream dtdStream = new AddDTDFilterInputStream(stream);
        DocumentBuilder docBuilder = XMLHelper.getDocBuilder(entityResolver);
        return docBuilder.parse(dtdStream, systemId);
    }

    public boolean hasParent() {
        return parentElement != null;
    }

    /**
     * Add a property if not yet set and value is not null.
     * This guarantee that property keep the first value that is put on it and that the properties
     * are never null.
     */
    public void setProperty(String prop, String val) {
        if (!properties.containsKey(prop) && val != null) {
            properties.put(prop, val);
        }
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    public void addImportedDependencyMgts(Map<MavenDependencyKey, PomDependencyMgt> inherited) {
        if (resolvedDependencyMgts != null) {
            throw new IllegalStateException("Cannot add imported dependency management elements after dependency management elements have been resolved for this POM.");
        }
        importedDependencyMgts.putAll(inherited);
    }

    public String getGroupId() {
        String groupId = getFirstChildText(projectElement , GROUP_ID);
        if (groupId == null) {
            groupId = getFirstChildText(parentElement, GROUP_ID);
        }
        return replaceProps(groupId);

    }

    public String getParentGroupId() {
        String groupId = getFirstChildText(parentElement , GROUP_ID);
        if (groupId == null) {
            groupId = getFirstChildText(projectElement, GROUP_ID);
        }
        return replaceProps(groupId);
    }

    public String getArtifactId() {
        String val = getFirstChildText(projectElement , ARTIFACT_ID);
        if (val == null) {
            val = getFirstChildText(parentElement, ARTIFACT_ID);
        }
        return replaceProps(val);
    }

    public String getParentArtifactId() {
        String val = getFirstChildText(parentElement , ARTIFACT_ID);
        if (val == null) {
            val = getFirstChildText(projectElement, ARTIFACT_ID);
        }
        return replaceProps(val);
    }

    public String getVersion() {
        String val = getFirstChildText(projectElement , VERSION);
        if (val == null) {
            val = getFirstChildText(parentElement, VERSION);
        }
        return replaceProps(val);
    }

    public String getParentVersion() {
        String val = getFirstChildText(parentElement , VERSION);
        if (val == null) {
            val = getFirstChildText(projectElement, VERSION);
        }
        return replaceProps(val);
    }

    public String getPackaging() {
        String val = getFirstChildText(projectElement , PACKAGING);
        if (val == null) {
            val = "jar";
        }
        return val;
    }

    public String getHomePage() {
        String val = getFirstChildText(projectElement, HOMEPAGE);
        if (val == null) {
            val = "";
        }
        return val;
    }

    public String getDescription() {
        String val = getFirstChildText(projectElement, DESCRIPTION);
        if (val == null) {
            val = "";
        }
        return val.trim();
    }

    public List<License> getLicenses() {
        Element licenses = getFirstChildElement(projectElement, LICENSES);
        if (licenses == null) {
            return Collections.emptyList();
        }
        licenses.normalize();
        List<License> lics = new ArrayList<License>();
        for (Element license : getAllChilds(licenses)) {
            if (LICENSE.equals(license.getNodeName())) {
                String name = getFirstChildText(license, LICENSE_NAME);
                String url = getFirstChildText(license, LICENSE_URL);

                if ((name == null) && (url == null)) {
                    // move to next license
                    continue;
                }

                if (name == null) {
                    // The license name is required in Ivy but not in a POM!
                    name = "Unknown License";
                }

                lics.add(new License(name, url));
            }
        }
        return lics;
    }

    public ModuleRevisionId getRelocation() {
        Element distrMgt = getFirstChildElement(projectElement, DISTRIBUTION_MGT);
        Element relocation = getFirstChildElement(distrMgt , RELOCATION);
        if (relocation == null) {
            return null;
        } else {
            String relocGroupId = getFirstChildText(relocation, GROUP_ID);
            String relocArtId = getFirstChildText(relocation, ARTIFACT_ID);
            String relocVersion = getFirstChildText(relocation, VERSION);
            relocGroupId = relocGroupId == null ? getGroupId() : relocGroupId;
            relocArtId = relocArtId == null ? getArtifactId() : relocArtId;
            relocVersion = relocVersion == null ? getVersion() : relocVersion;
            return IvyUtil.createModuleRevisionId(relocGroupId, relocArtId, relocVersion);
        }
    }

    /**
     * Returns all dependencies for this POM, including those inherited from parent POMs.
     */
    public Map<MavenDependencyKey, PomDependencyData> getDependencies() {
        if (resolvedDependencies == null) {
            resolvedDependencies = resolveDependencies();
        }
        return resolvedDependencies;
    }

    private Map<MavenDependencyKey, PomDependencyData> resolveDependencies() {
        Map<MavenDependencyKey, PomDependencyData> dependencies = new LinkedHashMap<MavenDependencyKey, PomDependencyData>();

        for(PomDependencyData dependency : getDependencyData(projectElement)) {
            dependencies.put(dependency.getId(), dependency);
        }

        // Maven adds inherited dependencies last
        for (Map.Entry<MavenDependencyKey, PomDependencyData> entry : pomParent.getDependencies().entrySet()) {
            if (!dependencies.containsKey(entry.getKey())) {
                dependencies.put(entry.getKey(), entry.getValue());
            }
        }

        for(PomProfile pomProfile : parseActivePomProfiles()) {
            for(PomDependencyData dependency : pomProfile.getDependencies()) {
                dependencies.put(dependency.getId(), dependency);
            }
        }

        return dependencies;
    }

    private List<PomDependencyData> getDependencyData(Element parentElement) {
        List<PomDependencyData> depElements = new ArrayList<PomDependencyData>();
        Element dependenciesElement = getFirstChildElement(parentElement, DEPENDENCIES);
        if (dependenciesElement != null) {
            NodeList childs = dependenciesElement.getChildNodes();
            for (int i = 0; i < childs.getLength(); i++) {
                Node node = childs.item(i);
                if (node instanceof Element && DEPENDENCY.equals(node.getNodeName())) {
                    depElements.add(new PomDependencyData((Element) node));
                }
            }
        }

        return depElements;
    }

    /**
     * Returns all dependency management elements for this POM, including those inherited from parent and imported POMs.
     */
    public Map<MavenDependencyKey, PomDependencyMgt> getDependencyMgt() {
        if(resolvedDependencyMgts == null) {
            resolvedDependencyMgts = resolveDependencyMgt();
        }
        return resolvedDependencyMgts;
    }

    private Map<MavenDependencyKey, PomDependencyMgt> resolveDependencyMgt() {
        Map<MavenDependencyKey, PomDependencyMgt> dependencies = new LinkedHashMap<MavenDependencyKey, PomDependencyMgt>();
        dependencies.putAll(pomParent.getDependencyMgt());
        dependencies.putAll(importedDependencyMgts);
        for(PomDependencyMgt dependencyMgt : parseDependencyMgt()) {
            dependencies.put(dependencyMgt.getId(), dependencyMgt);
        }
        return dependencies;
    }

    /**
     * Parses the dependency management elements declared in this POM without removing the duplicates.
     *
     * @return Parsed dependency management elements
     */
    public List<PomDependencyMgt> parseDependencyMgt() {
        if(declaredDependencyMgts == null) {
            List<PomDependencyMgt> dependencyMgts = getDependencyMgt(projectElement);

            for(PomProfile pomProfile : parseActivePomProfiles()) {
                for(PomDependencyMgt dependencyMgt : pomProfile.getDependencyMgts()) {
                    dependencyMgts.add(dependencyMgt);
                }
            }

            declaredDependencyMgts = dependencyMgts;
        }

        return declaredDependencyMgts;
    }

    private List<PomDependencyMgt> getDependencyMgt(Element parentElement) {
        List<PomDependencyMgt> depMgmtElements = new ArrayList<PomDependencyMgt>();
        Element dependenciesElement = getFirstChildElement(parentElement, DEPENDENCY_MGT);
        dependenciesElement = getFirstChildElement(dependenciesElement, DEPENDENCIES);

        if (dependenciesElement != null) {
            NodeList childs = dependenciesElement.getChildNodes();
            for (int i = 0; i < childs.getLength(); i++) {
                Node node = childs.item(i);
                if (node instanceof Element && DEPENDENCY.equals(node.getNodeName())) {
                    depMgmtElements.add(new PomDependencyMgtElement((Element) node));
                }
            }
        }

        return depMgmtElements;
    }

    public PomDependencyMgt findDependencyDefaults(MavenDependencyKey dependencyKey) {
        return getDependencyMgt().get(dependencyKey);
    }

    public void resolveGAV() {
        setGavPropertyValue(GavProperty.GROUP_ID, getGroupId());
        setGavPropertyValue(GavProperty.ARTIFACT_ID, getArtifactId());
        setGavPropertyValue(GavProperty.VERSION, getVersion());
    }

    private void setGavPropertyValue(GavProperty gavProperty, String propertyValue) {
        for(String name : gavProperty.getNames()) {
            properties.put(name, propertyValue);
        }
    }

    public class PomDependencyMgtElement implements PomDependencyMgt {
        private final Element depElement;

        PomDependencyMgtElement(Element depElement) {
            this.depElement = depElement;
        }

        public MavenDependencyKey getId() {
            return new MavenDependencyKey(getGroupId(), getArtifactId(), getType(), getClassifier());
        }

        /* (non-Javadoc)
         * @see org.apache.ivy.plugins.parser.m2.PomDependencyMgt#getGroupId()
         */
        public String getGroupId() {
            String val = getFirstChildText(depElement , GROUP_ID);
            return replaceProps(val);
        }

        /* (non-Javadoc)
         * @see org.apache.ivy.plugins.parser.m2.PomDependencyMgt#getArtifaceId()
         */
        public String getArtifactId() {
            String val = getFirstChildText(depElement , ARTIFACT_ID);
            return replaceProps(val);
        }

        /* (non-Javadoc)
         * @see org.apache.ivy.plugins.parser.m2.PomDependencyMgt#getVersion()
         */
        public String getVersion() {
            String val = getFirstChildText(depElement , VERSION);
            return replaceProps(val);
        }

        public String getScope() {
            String val = getFirstChildText(depElement , SCOPE);
            return replaceProps(val);
        }

        public String getType() {
            String val = getFirstChildText(depElement , TYPE);
            val = replaceProps(val);

            if(val == null) {
                val = "jar";
            }

            return val;
        }

        public String getClassifier() {
            String val = getFirstChildText(depElement , CLASSIFIER);
            return replaceProps(val);
        }

        public List<ModuleId> getExcludedModules() {
            Element exclusionsElement = getFirstChildElement(depElement, EXCLUSIONS);
            List<ModuleId> exclusions = new LinkedList<ModuleId>();
            if (exclusionsElement != null) {
                NodeList childs = exclusionsElement.getChildNodes();
                for (int i = 0; i < childs.getLength(); i++) {
                    Node node = childs.item(i);
                    if (node instanceof Element && EXCLUSION.equals(node.getNodeName())) {
                        String groupId = getFirstChildText((Element) node, GROUP_ID);
                        String artifactId = getFirstChildText((Element) node, ARTIFACT_ID);
                        if ((groupId != null) && (artifactId != null)) {
                            exclusions.add(IvyUtil.createModuleId(groupId, artifactId));
                        }
                    }
                }
            }
            return exclusions;
        }
    }

    public class PomDependencyData extends PomDependencyMgtElement {
        private final Element depElement;
        PomDependencyData(Element depElement) {
            super(depElement);
            this.depElement = depElement;
        }

        public boolean isOptional() {
            Element e = getFirstChildElement(depElement, OPTIONAL);
            return (e != null) && "true".equalsIgnoreCase(getTextContent(e));
        }
    }

    public class PomProfileElement implements PomProfile {
        private final Element element;
        private List<PomDependencyMgt> declaredDependencyMgts;
        private List<PomDependencyData> declaredDependencies;

        PomProfileElement(Element element) {
            this.element = element;
        }

        public String getId() {
            return getFirstChildText(element, PROFILE_ID);
        }

        public Map<String, String> getProperties() {
            return getPomProperties(element);
        }

        public List<PomDependencyMgt> getDependencyMgts() {
            if(declaredDependencyMgts == null) {
                declaredDependencyMgts = getDependencyMgt(element);
            }

            return declaredDependencyMgts;
        }

        public List<PomDependencyData> getDependencies() {
            if(declaredDependencies == null) {
                declaredDependencies = getDependencyData(element);
            }

            return declaredDependencies;
        }
    }

    /**
     * Parses all active profiles that can be found in POM.
     *
     * @return Active POM profiles
     */
    private List<PomProfile> parseActivePomProfiles() {
        if(declaredActivePomProfiles == null) {
            List<PomProfile> activePomProfiles = new ArrayList<PomProfile>();
            Element profilesElement = getFirstChildElement(projectElement, PROFILES);

            if(profilesElement != null) {
                for(Element profileElement : getAllChilds(profilesElement)) {
                    if(PROFILE.equals(profileElement.getNodeName())) {
                        Element activationElement = getFirstChildElement(profileElement, PROFILE_ACTIVATION);

                        if(activationElement != null) {
                            String activeByDefault = getFirstChildText(activationElement, PROFILE_ACTIVATION_ACTIVE_BY_DEFAULT);

                            if(activeByDefault != null && "true".equals(activeByDefault)) {
                                activePomProfiles.add(new PomProfileElement(profileElement));
                            }
                        }
                    }
                }
            }

            declaredActivePomProfiles = activePomProfiles;
        }

        return declaredActivePomProfiles;
    }

    /**
     * @return the content of the properties tag into the pom.
     */
    public Map<String, String> getPomProperties() {
        return getPomProperties(projectElement);
    }

    private Map<String, String> getPomProperties(Element parentElement) {
        Map<String, String> pomProperties = new HashMap<String, String>();
        Element propsEl = getFirstChildElement(parentElement, PROPERTIES);
        if (propsEl != null) {
            propsEl.normalize();
        }
        for (Element prop : getAllChilds(propsEl)) {
            pomProperties.put(prop.getNodeName(), getTextContent(prop));
        }
        return pomProperties;
    }

    private String replaceProps(String val) {
        if (val == null) {
            return null;
        } else {
            return IvyPatternHelper.substituteVariables(val, properties).trim();
        }
    }

    private static String getTextContent(Element element) {
        StringBuilder result = new StringBuilder();

        NodeList childNodes = element.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node child = childNodes.item(i);

            switch (child.getNodeType()) {
                case Node.CDATA_SECTION_NODE:
                case Node.TEXT_NODE:
                    result.append(child.getNodeValue());
                    break;
                default:
                    break;
            }
        }

        return result.toString();
    }

    private static String getFirstChildText(Element parentElem, String name) {
        Element node = getFirstChildElement(parentElem, name);
        if (node != null) {
            return getTextContent(node);
        } else {
            return null;
        }
    }

    private static Element getFirstChildElement(Element parentElem, String name) {
        if (parentElem == null) {
            return null;
        }
        NodeList childs = parentElem.getChildNodes();
        for (int i = 0; i < childs.getLength(); i++) {
            Node node = childs.item(i);
            if (node instanceof Element && name.equals(node.getNodeName())) {
                return (Element) node;
            }
        }
        return null;
    }

    private static List<Element> getAllChilds(Element parent) {
        List<Element> r = new LinkedList<Element>();
        if (parent != null) {
            NodeList childs = parent.getChildNodes();
            for (int i = 0; i < childs.getLength(); i++) {
                Node node = childs.item(i);
                if (node instanceof Element) {
                    r.add((Element) node);
                }
            }
        }
        return r;
    }

    private static final class AddDTDFilterInputStream extends FilterInputStream {
        private static final int MARK = 10000;
        private static final String DOCTYPE = "<!DOCTYPE project SYSTEM \"m2-entities.ent\">\n";

        private int count;
        private byte[] prefix = DOCTYPE.getBytes();

        private AddDTDFilterInputStream(InputStream in) throws IOException {
            super(new BufferedInputStream(in));

            this.in.mark(MARK);

            // TODO: we should really find a better solution for this...
            // maybe we could use a FilterReader instead of a FilterInputStream?
            int byte1 = this.in.read();
            int byte2 = this.in.read();
            int byte3 = this.in.read();

            if (byte1 == 239 && byte2 == 187 && byte3 == 191) {
                // skip the UTF-8 BOM
                this.in.mark(MARK);
            } else {
                this.in.reset();
            }

            int bytesToSkip = 0;
            LineNumberReader reader = new LineNumberReader(new InputStreamReader(this.in, "UTF-8"), 100);
            String firstLine = reader.readLine();
            if (firstLine != null) {
                String trimmed = firstLine.trim();
                if (trimmed.startsWith("<?xml ")) {
                    int endIndex = trimmed.indexOf("?>");
                    String xmlDecl = trimmed.substring(0, endIndex + 2);
                    prefix = (xmlDecl + "\n" + DOCTYPE).getBytes();
                    bytesToSkip = xmlDecl.getBytes().length;
                }
            }

            this.in.reset();
            for (int i = 0; i < bytesToSkip; i++) {
                this.in.read();
            }
        }

        public int read() throws IOException {
            if (count < prefix.length) {
                return prefix[count++];
            }

            return super.read();
        }

        public int read(byte[] b, int off, int len) throws IOException {
            if (b == null) {
                throw new NullPointerException();
            } else if ((off < 0) || (off > b.length) || (len < 0)
                    || ((off + len) > b.length) || ((off + len) < 0)) {
                throw new IndexOutOfBoundsException();
            } else if (len == 0) {
                return 0;
            }

            int nbrBytesCopied = 0;

            if (count < prefix.length) {
                int nbrBytesFromPrefix = Math.min(prefix.length - count, len);
                System.arraycopy(prefix, count, b, off, nbrBytesFromPrefix);
                nbrBytesCopied = nbrBytesFromPrefix;
            }

            if (nbrBytesCopied < len) {
                nbrBytesCopied += in.read(b, off + nbrBytesCopied, len - nbrBytesCopied);
            }

            count += nbrBytesCopied;
            return nbrBytesCopied;
        }
    }

}
