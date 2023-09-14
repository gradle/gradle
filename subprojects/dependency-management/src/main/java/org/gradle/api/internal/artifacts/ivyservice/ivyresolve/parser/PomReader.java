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

import com.google.common.collect.Lists;
import org.apache.commons.io.IOUtils;
import org.apache.ivy.core.IvyPatternHelper;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.data.MavenDependencyKey;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.data.PomDependencyMgt;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.data.PomProfile;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.classloader.ClassLoaderUtils;
import org.gradle.internal.resource.local.LocallyAvailableExternalResource;
import org.w3c.dom.Comment;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.PomDomParser.AddDTDFilterInputStream;
import static org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.PomDomParser.getAllChilds;
import static org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.PomDomParser.getFirstChildElement;
import static org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.PomDomParser.getFirstChildText;
import static org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.PomDomParser.getTextContent;

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
    private static final String PROFILE_ACTIVATION_PROPERTY = "property";
    private static final byte[] M2_ENTITIES_RESOURCE;
    private static final DocumentBuilderFactory DOCUMENT_BUILDER_FACTORY;

    static {
        byte[] bytes;
        try {
            bytes = IOUtils.toByteArray(org.apache.ivy.plugins.parser.m2.PomReader.class.getResourceAsStream("m2-entities.ent"));
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
        M2_ENTITIES_RESOURCE = bytes;

        // Set the context classloader the bootstrap classloader, to work around the way that JAXP locates implementation classes
        // This should ensure that the JAXP classes provided by the JVM are used, rather than some other implementation
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(ClassLoaderUtils.getPlatformClassLoader());
        try {
            DOCUMENT_BUILDER_FACTORY = DocumentBuilderFactory.newInstance();
            DOCUMENT_BUILDER_FACTORY.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            DOCUMENT_BUILDER_FACTORY.setValidating(false);
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    private static final EntityResolver M2_ENTITY_RESOLVER = new EntityResolver() {
        @Override
        public InputSource resolveEntity(String publicId, String systemId) {
            if ((systemId != null) && systemId.endsWith("m2-entities.ent")) {
                return new InputSource(new ByteArrayInputStream(M2_ENTITIES_RESOURCE));
            }
            return null;
        }
    };

    private PomParent pomParent = new RootPomParent();
    private final Map<String, String> pomProperties = new HashMap<>();
    private final Map<String, String> effectiveProperties = new HashMap<>();
    private List<PomDependencyMgt> declaredDependencyMgts;
    private List<PomProfile> declaredActivePomProfiles;
    private Map<MavenDependencyKey, PomDependencyMgt> resolvedDependencyMgts;
    private final Map<MavenDependencyKey, PomDependencyMgt> importedDependencyMgts = new LinkedHashMap<>();
    private Map<MavenDependencyKey, PomDependencyData> resolvedDependencies;
    private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;

    private final Element projectElement;
    private final Element parentElement;

    public PomReader(final LocallyAvailableExternalResource resource, ImmutableModuleIdentifierFactory moduleIdentifierFactory, Map<String, String> childPomProperties) throws SAXException {
        this.moduleIdentifierFactory = moduleIdentifierFactory;
        setPomProperties(childPomProperties);
        final String systemId = resource.getFile().toURI().toASCIIString();
        Document pomDomDoc = resource.withContent(inputStream -> {
            try {
                return parseToDom(inputStream, systemId);
            } catch (Exception e) {
                throw new MetaDataParseException("POM", resource, e);
            }
        }).getResult();
        projectElement = pomDomDoc.getDocumentElement();
        if (!PROJECT.equals(projectElement.getNodeName()) && !MODEL.equals(projectElement.getNodeName())) {
            throw new SAXParseException("project must be the root tag", systemId, systemId, 0, 0);
        }
        parentElement = getFirstChildElement(projectElement, PARENT);

        setDefaultParentGavProperties();
        setPomProperties(parseProperties(projectElement));
        setActiveProfileProperties();
    }

    public PomReader(final LocallyAvailableExternalResource resource, ImmutableModuleIdentifierFactory moduleIdentifierFactory) throws SAXException {
        this(resource, moduleIdentifierFactory, Collections.emptyMap());
    }

    public void setPomParent(PomParent pomParent) {
        this.pomParent = pomParent;
        for (Map.Entry<String, String> entry : pomParent.getProperties().entrySet()) {
            maybeSetEffectiveProperty(entry.getKey(), entry.getValue());
        }
    }

    private void setDefaultParentGavProperties() {
        maybeSetGavProperties(GavProperty.PARENT_GROUP_ID, getParentGroupId());
        maybeSetGavProperties(GavProperty.PARENT_VERSION, getParentVersion());
        maybeSetGavProperties(GavProperty.PARENT_ARTIFACT_ID, getParentArtifactId());
    }

    private void maybeSetGavProperties(GavProperty gavProperty, String propertyValue) {
        for (String name : gavProperty.getNames()) {
            maybeSetEffectiveProperty(name, propertyValue);
        }
    }

    private void setPomProperties(Map<String, String> pomProperties) {
        if (!pomProperties.isEmpty()) {
            this.pomProperties.putAll(pomProperties);
            for (Map.Entry<String, String> pomProperty : pomProperties.entrySet()) {
                maybeSetEffectiveProperty(pomProperty.getKey(), pomProperty.getValue());
            }
        }
    }

    /**
     * Sets properties for all active profiles. Properties from an active profile override existing POM properties.
     */
    private void setActiveProfileProperties() {
        for (PomProfile activePomProfile : parseActivePomProfiles()) {
            for (Map.Entry<String, String> property : activePomProfile.getProperties().entrySet()) {
                effectiveProperties.put(property.getKey(), property.getValue());
            }
        }
    }

    /**
     * Add a property if not yet set and value is not null.
     * This guarantee that property keep the first value that is put on it and that the properties
     * are never null.
     */
    private void maybeSetEffectiveProperty(String prop, String val) {
        if (!effectiveProperties.containsKey(prop) && val != null) {
            effectiveProperties.put(prop, val);
        }
    }

    private enum GavProperty {
        PARENT_GROUP_ID("parent.groupId", "project.parent.groupId"),
        PARENT_ARTIFACT_ID("parent.artifactId", "project.parent.artifactId"),
        PARENT_VERSION("parent.version", "project.parent.version"),
        GROUP_ID("project.groupId", "pom.groupId", "groupId"),
        ARTIFACT_ID("project.artifactId", "pom.artifactId", "artifactId"),
        VERSION("project.version", "pom.version", "version");

        private final String[] names;

        GavProperty(String... names) {
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

    private static DocumentBuilder getDocBuilder(EntityResolver entityResolver) {
        try {
            DocumentBuilder docBuilder = DOCUMENT_BUILDER_FACTORY.newDocumentBuilder();
            if (entityResolver != null) {
                docBuilder.setEntityResolver(entityResolver);
            }
            return docBuilder;
        } catch (ParserConfigurationException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private static Document parseToDom(InputStream stream, String systemId) throws IOException, SAXException {
        // Set the context classloader the bootstrap classloader, to work around the way that JAXP locates implementation classes
        // This should ensure that the JAXP classes provided by the JVM are used, rather than some other implementation
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(ClassLoaderUtils.getPlatformClassLoader());
        try {
            InputStream dtdStream = new AddDTDFilterInputStream(stream);
            return getDocBuilder(M2_ENTITY_RESOLVER).parse(dtdStream, systemId);
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    public boolean hasParent() {
        return parentElement != null;
    }

    @Override
    public Map<String, String> getProperties() {
        return effectiveProperties;
    }

    public void addImportedDependencyMgts(Map<MavenDependencyKey, PomDependencyMgt> inherited) {
        if (resolvedDependencyMgts != null) {
            throw new IllegalStateException("Cannot add imported dependency management elements after dependency management elements have been resolved for this POM.");
        }
        importedDependencyMgts.putAll(inherited);
    }

    private void checkNotNull(String value, String name) {
        checkNotNull(value, name, null);
    }

    private void checkNotNull(String value, String name, String element) {
        if (value == null) {
            String attributeName = element == null ? name : element + " " + name;
            throw new RuntimeException("Missing required attribute: " + attributeName);
        }
    }

    public String getGroupId() {
        String groupId = getFirstChildText(projectElement, GROUP_ID);
        if (groupId == null) {
            groupId = getFirstChildText(parentElement, GROUP_ID);
        }
        checkNotNull(groupId, GROUP_ID);
        return replaceProps(groupId);
    }

    public String getParentGroupId() {
        String groupId = getFirstChildText(parentElement, GROUP_ID);
        if (groupId == null) {
            groupId = getFirstChildText(projectElement, GROUP_ID);
        }
        checkNotNull(groupId, GROUP_ID);
        return replaceProps(groupId);
    }

    public String getArtifactId() {
        String val = getFirstChildText(projectElement, ARTIFACT_ID);
        if (val == null) {
            val = getFirstChildText(parentElement, ARTIFACT_ID);
        }
        checkNotNull(val, ARTIFACT_ID);
        return replaceProps(val);
    }

    public String getParentArtifactId() {
        String val = getFirstChildText(parentElement, ARTIFACT_ID);
        if (val == null) {
            val = getFirstChildText(projectElement, ARTIFACT_ID);
        }
        checkNotNull(val, ARTIFACT_ID);
        return replaceProps(val);
    }

    public String getVersion() {
        String val = getFirstChildText(projectElement, VERSION);
        if (val == null) {
            val = getFirstChildText(parentElement, VERSION);
        }
        return replaceProps(val);
    }

    public String getParentVersion() {
        String val = getFirstChildText(parentElement, VERSION);
        if (val == null) {
            val = getFirstChildText(projectElement, VERSION);
        }
        return replaceProps(val);
    }

    public String getPackaging() {
        String val = getFirstChildText(projectElement, PACKAGING);
        if (val == null) {
            val = "jar";
        }
        return replaceProps(val);
    }

    public boolean hasGradleMetadataMarker() {
        NodeList childNodes = projectElement.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node node = childNodes.item(i);
            if (node instanceof Comment) {
                String comment = node.getNodeValue();
                if (comment.contains(MetaDataParser.GRADLE_6_METADATA_MARKER) || comment.contains(MetaDataParser.GRADLE_METADATA_MARKER)) {
                    return true;
                }
            }
        }
        return false;
    }

    public ModuleVersionIdentifier getRelocation() {
        Element distrMgt = getFirstChildElement(projectElement, DISTRIBUTION_MGT);
        Element relocation = getFirstChildElement(distrMgt, RELOCATION);
        if (relocation == null) {
            return null;
        } else {
            String relocGroupId = getFirstChildText(relocation, GROUP_ID);
            String relocArtId = getFirstChildText(relocation, ARTIFACT_ID);
            String relocVersion = getFirstChildText(relocation, VERSION);
            relocGroupId = relocGroupId == null ? getGroupId() : relocGroupId;
            relocArtId = relocArtId == null ? getArtifactId() : relocArtId;
            relocVersion = relocVersion == null ? getVersion() : relocVersion;
            return DefaultModuleVersionIdentifier.newId(relocGroupId, relocArtId, relocVersion);
        }
    }

    /**
     * Returns all dependencies for this POM, including those inherited from parent POMs.
     */
    @Override
    public Map<MavenDependencyKey, PomDependencyData> getDependencies() {
        if (resolvedDependencies == null) {
            resolvedDependencies = resolveDependencies();
        }
        return resolvedDependencies;
    }

    private Map<MavenDependencyKey, PomDependencyData> resolveDependencies() {
        Map<MavenDependencyKey, PomDependencyData> dependencies = new LinkedHashMap<>();

        for (PomDependencyData dependency : getDependencyData(projectElement)) {
            dependencies.put(dependency.getId(), dependency);
        }

        // Maven adds inherited dependencies last
        for (Map.Entry<MavenDependencyKey, PomDependencyData> entry : pomParent.getDependencies().entrySet()) {
            if (!dependencies.containsKey(entry.getKey())) {
                dependencies.put(entry.getKey(), entry.getValue());
            }
        }

        for (PomProfile pomProfile : parseActivePomProfiles()) {
            for (PomDependencyData dependency : pomProfile.getDependencies()) {
                dependencies.put(dependency.getId(), dependency);
            }
        }

        return dependencies;
    }

    private List<PomDependencyData> getDependencyData(Element parentElement) {
        List<PomDependencyData> depElements = new ArrayList<>();
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
    @Override
    public Map<MavenDependencyKey, PomDependencyMgt> getDependencyMgt() {
        if (resolvedDependencyMgts == null) {
            resolvedDependencyMgts = resolveDependencyMgt();
        }
        return resolvedDependencyMgts;
    }

    private Map<MavenDependencyKey, PomDependencyMgt> resolveDependencyMgt() {
        Map<MavenDependencyKey, PomDependencyMgt> dependencies = new LinkedHashMap<>();
        dependencies.putAll(pomParent.getDependencyMgt());
        dependencies.putAll(importedDependencyMgts);
        for (PomDependencyMgt dependencyMgt : parseDependencyMgt()) {
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
        if (declaredDependencyMgts == null) {
            List<PomDependencyMgt> dependencyMgts = getDependencyMgt(projectElement);

            for (PomProfile pomProfile : parseActivePomProfiles()) {
                dependencyMgts.addAll(pomProfile.getDependencyMgts());
            }

            declaredDependencyMgts = dependencyMgts;
        }

        return declaredDependencyMgts;
    }

    private List<PomDependencyMgt> getDependencyMgt(Element parentElement) {
        List<PomDependencyMgt> depMgmtElements = new ArrayList<>();
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

    @Override
    public PomDependencyMgt findDependencyDefaults(MavenDependencyKey dependencyKey) {
        return getDependencyMgt().get(dependencyKey);
    }

    public void resolveGAV() {
        setGavPropertyValue(GavProperty.GROUP_ID, getGroupId());
        setGavPropertyValue(GavProperty.ARTIFACT_ID, getArtifactId());
        setGavPropertyValue(GavProperty.VERSION, getVersion());
    }

    private void setGavPropertyValue(GavProperty gavProperty, String propertyValue) {
        for (String name : gavProperty.getNames()) {
            effectiveProperties.put(name, propertyValue);
        }
    }

    public class PomDependencyMgtElement implements PomDependencyMgt {
        private final Element depElement;

        PomDependencyMgtElement(Element depElement) {
            this.depElement = depElement;
        }

        @Override
        public MavenDependencyKey getId() {
            return new MavenDependencyKey(getGroupId(), getArtifactId(), getType(), getClassifier());
        }

        /* (non-Javadoc)
         * @see org.apache.ivy.plugins.parser.m2.PomDependencyMgt#getGroupId()
         */
        @Override
        public String getGroupId() {
            String val = getFirstChildText(depElement, GROUP_ID);
            checkNotNull(val, GROUP_ID, DEPENDENCY);
            return replaceProps(val);
        }

        /* (non-Javadoc)
         * @see org.apache.ivy.plugins.parser.m2.PomDependencyMgt#getArtifactId()
         */
        @Override
        public String getArtifactId() {
            String val = getFirstChildText(depElement, ARTIFACT_ID);
            checkNotNull(val, ARTIFACT_ID, DEPENDENCY);
            return replaceProps(val);
        }

        /* (non-Javadoc)
         * @see org.apache.ivy.plugins.parser.m2.PomDependencyMgt#getVersion()
         */
        @Override
        public String getVersion() {
            String val = getFirstChildText(depElement, VERSION);
            return replaceProps(val);
        }

        @Override
        public String getScope() {
            String val = getFirstChildText(depElement, SCOPE);
            return replaceProps(val);
        }

        @Override
        public String getType() {
            String val = getFirstChildText(depElement, TYPE);
            val = replaceProps(val);

            if (val == null) {
                val = "jar";
            }

            return val;
        }

        @Override
        public String getClassifier() {
            String val = getFirstChildText(depElement, CLASSIFIER);
            return replaceProps(val);
        }

        @Override
        public List<ModuleIdentifier> getExcludedModules() {
            Element exclusionsElement = getFirstChildElement(depElement, EXCLUSIONS);
            if (exclusionsElement != null) {
                NodeList childs = exclusionsElement.getChildNodes();
                List<ModuleIdentifier> exclusions = Lists.newArrayList();
                for (int i = 0; i < childs.getLength(); i++) {
                    Node node = childs.item(i);
                    if (node instanceof Element && EXCLUSION.equals(node.getNodeName())) {
                        String groupId = getFirstChildText((Element) node, GROUP_ID);
                        String artifactId = getFirstChildText((Element) node, ARTIFACT_ID);
                        if ((groupId != null) || (artifactId != null)) {
                            exclusions.add(moduleIdentifierFactory.module(groupId != null ? groupId : "*", artifactId != null ? artifactId : "*"));
                        }
                    }
                }
                return exclusions;
            }
            return Collections.emptyList();
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

        @Override
        public String getId() {
            return getFirstChildText(element, PROFILE_ID);
        }

        @Override
        public Map<String, String> getProperties() {
            return parseProperties(element);
        }

        @Override
        public List<PomDependencyMgt> getDependencyMgts() {
            if (declaredDependencyMgts == null) {
                declaredDependencyMgts = getDependencyMgt(element);
            }

            return declaredDependencyMgts;
        }

        @Override
        public List<PomDependencyData> getDependencies() {
            if (declaredDependencies == null) {
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
        if (declaredActivePomProfiles == null) {
            List<PomProfile> activeByDefaultPomProfiles = new ArrayList<>();
            List<PomProfile> activeByAbsenceOfPropertyPomProfiles = new ArrayList<>();
            Element profilesElement = getFirstChildElement(projectElement, PROFILES);

            if (profilesElement != null) {
                for (Element profileElement : getAllChilds(profilesElement)) {
                    if (PROFILE.equals(profileElement.getNodeName())) {
                        Element activationElement = getFirstChildElement(profileElement, PROFILE_ACTIVATION);

                        if (activationElement != null) {
                            String activeByDefault = getFirstChildText(activationElement, PROFILE_ACTIVATION_ACTIVE_BY_DEFAULT);

                            if ("true".equals(activeByDefault)) {
                                activeByDefaultPomProfiles.add(new PomProfileElement(profileElement));
                            } else {
                                Element propertyElement = getFirstChildElement(activationElement, PROFILE_ACTIVATION_PROPERTY);

                                if (propertyElement != null) {
                                    if (isActivationPropertyActivated(propertyElement)) {
                                        activeByAbsenceOfPropertyPomProfiles.add(new PomProfileElement(profileElement));
                                    }
                                }
                            }
                        }
                    }
                }
            }

            declaredActivePomProfiles = determineActiveProfiles(activeByDefaultPomProfiles, activeByAbsenceOfPropertyPomProfiles);
        }

        return declaredActivePomProfiles;
    }

    /**
     * If a profile is identified as active through any other activation method than activeByDefault, none of the existing
     * profiles marked as activeByDefault apply.
     *
     * @param activeByDefaultPomProfiles Parsed profiles that are active by default
     * @param activeByAbsenceOfPropertyPomProfiles Parsed profiles that are activated by absence of property
     * @return List of active profiles that are not activeByDefault
     */
    private List<PomProfile> determineActiveProfiles(List<PomProfile> activeByDefaultPomProfiles, List<PomProfile> activeByAbsenceOfPropertyPomProfiles) {
        return !activeByAbsenceOfPropertyPomProfiles.isEmpty() ? activeByAbsenceOfPropertyPomProfiles : activeByDefaultPomProfiles;
    }

    /**
     * Checks if activation property is active through absence of system property.
     *
     * @param propertyElement Property element
     * @return Activation indicator
     * @see <a href="http://books.sonatype.com/mvnref-book/reference/profiles-sect-activation.html#profiles-sect-activation-config">Maven documentation</a>
     */
    private boolean isActivationPropertyActivated(Element propertyElement) {
        String propertyName = getFirstChildText(propertyElement, "name");
        return propertyName.startsWith("!");
    }

    /**
     * @return properties of both current and children poms.
     */
    Map<String, String> getAllPomProperties() {
        return pomProperties;
    }

    private Map<String, String> parseProperties(Element parentElement) {
        Map<String, String> pomProperties = new HashMap<>();
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
            return IvyPatternHelper.substituteVariables(val, effectiveProperties).trim();
        }
    }
}
