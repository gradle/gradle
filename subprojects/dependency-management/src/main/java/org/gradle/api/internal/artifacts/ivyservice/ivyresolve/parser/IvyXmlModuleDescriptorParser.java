/*
 * Copyright 2012 the original author or authors.
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

import com.google.common.base.Joiner;
import com.google.common.collect.Sets;
import org.apache.ivy.core.IvyPatternHelper;
import org.apache.ivy.core.NormalRelativeUrlResolver;
import org.apache.ivy.core.RelativeUrlResolver;
import org.apache.ivy.core.module.descriptor.Configuration;
import org.apache.ivy.core.module.descriptor.ConfigurationAware;
import org.apache.ivy.core.module.descriptor.DefaultDependencyArtifactDescriptor;
import org.apache.ivy.core.module.descriptor.DefaultDependencyDescriptor;
import org.apache.ivy.core.module.descriptor.DefaultExcludeRule;
import org.apache.ivy.core.module.descriptor.DefaultIncludeRule;
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor;
import org.apache.ivy.core.module.descriptor.DependencyArtifactDescriptor;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.descriptor.ExcludeRule;
import org.apache.ivy.core.module.descriptor.IncludeRule;
import org.apache.ivy.core.module.descriptor.License;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.module.id.ArtifactId;
import org.apache.ivy.core.module.id.ModuleId;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.plugins.matcher.PatternMatcher;
import org.apache.ivy.plugins.namespace.Namespace;
import org.apache.ivy.plugins.parser.xml.XmlModuleDescriptorParser;
import org.apache.ivy.util.extendable.DefaultExtendableItem;
import org.apache.ivy.util.url.URLHandlerRegistry;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.ivyservice.IvyUtil;
import org.gradle.api.internal.artifacts.ivyservice.NamespaceId;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.PatternMatchers;
import org.gradle.api.internal.artifacts.repositories.metadata.IvyMutableModuleMetadataFactory;
import org.gradle.api.internal.component.ArtifactType;
import org.gradle.api.resources.MissingResourceException;
import org.gradle.internal.classloader.ClassLoaderUtils;
import org.gradle.internal.component.external.descriptor.Artifact;
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier;
import org.gradle.internal.component.external.model.ivy.MutableIvyModuleResolveMetadata;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.resource.ExternalResource;
import org.gradle.internal.resource.local.FileResourceRepository;
import org.gradle.internal.resource.local.LocallyAvailableExternalResource;
import org.gradle.internal.resource.transfer.UrlExternalResource;
import org.gradle.util.internal.CollectionUtils;
import org.gradle.util.internal.TextUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.ext.LexicalHandler;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.gradle.api.internal.artifacts.ivyservice.IvyUtil.createModuleRevisionId;

/**
 * Copied from org.apache.ivy.plugins.parser.xml.XmlModuleDescriptorParser into Gradle codebase, and heavily modified.
 */
public class IvyXmlModuleDescriptorParser extends AbstractModuleDescriptorParser<MutableIvyModuleResolveMetadata> {
    static final String[] DEPENDENCY_REGULAR_ATTRIBUTES =
            new String[] {"org", "name", "branch", "branchConstraint", "rev", "revConstraint", "force", "transitive", "changing", "conf"};

    public static final String IVY_DATE_FORMAT_PATTERN = "yyyyMMddHHmmss";

    private static final Logger LOGGER = LoggerFactory.getLogger(IvyXmlModuleDescriptorParser.class);
    private final IvyModuleDescriptorConverter moduleDescriptorConverter;
    private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;
    private final IvyMutableModuleMetadataFactory metadataFactory;

    public IvyXmlModuleDescriptorParser(IvyModuleDescriptorConverter moduleDescriptorConverter,
                                        ImmutableModuleIdentifierFactory moduleIdentifierFactory,
                                        FileResourceRepository fileResourceRepository,
                                        IvyMutableModuleMetadataFactory metadataFactory) {
        super(fileResourceRepository);
        this.moduleDescriptorConverter = moduleDescriptorConverter;
        this.moduleIdentifierFactory = moduleIdentifierFactory;
        this.metadataFactory = metadataFactory;
    }

    @Override
    protected ParseResult<MutableIvyModuleResolveMetadata> doParseDescriptor(DescriptorParseContext parseContext, LocallyAvailableExternalResource resource, boolean validate) throws IOException, ParseException {
        Parser parser = createParser(parseContext, resource, populateProperties());
        parser.setValidate(validate);
        parser.parse();

        DefaultModuleDescriptor moduleDescriptor = parser.getModuleDescriptor();
        postProcess(moduleDescriptor);

        return ParseResult.of(parser.getMetaData(), parser.hasGradleMetadataRedirect);
    }

    protected Parser createParser(DescriptorParseContext parseContext, LocallyAvailableExternalResource resource, Map<String, String> properties) throws MalformedURLException {
        return new Parser(parseContext, moduleDescriptorConverter, resource, resource.getFile().toURI().toURL(), moduleIdentifierFactory, metadataFactory, properties);
    }

    protected void postProcess(DefaultModuleDescriptor moduleDescriptor) {
    }

    @Override
    protected String getTypeName() {
        return "Ivy file";
    }

    private Map<String, String> populateProperties() {
        HashMap<String, String> properties = new HashMap<>();
        String baseDir = new File(".").getAbsolutePath();
        properties.put("ivy.default.settings.dir", baseDir);
        properties.put("ivy.basedir", baseDir);

        Set<String> propertyNames = CollectionUtils.collect(System.getProperties().entrySet(), entry -> entry.getKey().toString());

        for (String property : propertyNames) {
            properties.put(property, System.getProperty(property));
        }
        return properties;
    }

    protected abstract static class AbstractParser extends DefaultHandler {
        private static final String DEFAULT_CONF_MAPPING = "*->*";

        private String defaultConf; // used only as defaultconf, not used for

        // guessing right side part of a mapping
        private String defaultConfMapping; // same as default conf but is used

        // for guessing right side part of a mapping
        private DefaultDependencyDescriptor defaultConfMappingDescriptor;

        private final ExternalResource res;

        private final List<String> errors = new ArrayList<>();

        private final DefaultModuleDescriptor md;
        protected IvyModuleResolveMetaDataBuilder metaData;

        protected AbstractParser(ExternalResource resource) {
            this.res = resource; // used for log and date only
            md = new DefaultModuleDescriptor(XmlModuleDescriptorParser.getInstance(), null);
        }

        protected void checkErrors() throws ParseException {
            if (!errors.isEmpty()) {
                throw new ParseException(Joiner.on(TextUtil.getPlatformLineSeparator()).join(errors), 0);
            }
            if (getMd().getModuleRevisionId() == null) {
                throw new MetaDataParseException("Ivy file", getResource(), new GradleException("Not a valid Ivy file"));
            }
        }

        protected ExternalResource getResource() {
            return res;
        }

        protected String getDefaultConfMapping() {
            return defaultConfMapping;
        }

        protected void setDefaultConfMapping(String defaultConf) {
            defaultConfMapping = defaultConf;
        }

        protected void parseDepsConfs(String confs, DefaultDependencyDescriptor dd) {
            parseDepsConfs(confs, dd, defaultConfMapping != null);
        }

        protected void parseDepsConfs(String confs, DefaultDependencyDescriptor dd,
                boolean useDefaultMappingToGuessRightOperand) {
            parseDepsConfs(confs, dd, useDefaultMappingToGuessRightOperand, true);
        }

        protected void parseDepsConfs(String confs, DefaultDependencyDescriptor dd,
                boolean useDefaultMappingToGuessRightOperand, boolean evaluateConditions) {
            if (confs == null) {
                return;
            }

            String[] conf = confs.split(";");
            parseDepsConfs(conf, dd, useDefaultMappingToGuessRightOperand, evaluateConditions);
        }

        protected void parseDepsConfs(String[] conf, DefaultDependencyDescriptor dd,
                boolean useDefaultMappingToGuessRightOperand) {
            parseDepsConfs(conf, dd, useDefaultMappingToGuessRightOperand, true);
        }

        protected void parseDepsConfs(String[] conf, DefaultDependencyDescriptor dd,
                boolean useDefaultMappingToGuessRightOperand, boolean evaluateConditions) {
            replaceConfigurationWildcards(md);
            for (String s : conf) {
                String[] ops = s.split("->");
                if (ops.length == 1) {
                    String[] modConfs = ops[0].split(",");
                    if (!useDefaultMappingToGuessRightOperand) {
                        for (String modConf : modConfs) {
                            dd.addDependencyConfiguration(modConf.trim(), modConf.trim());
                        }
                    } else {
                        for (String modConf : modConfs) {
                            String[] depConfs = getDefaultConfMappingDescriptor()
                                .getDependencyConfigurations(modConf);
                            if (depConfs.length > 0) {
                                for (String depConf : depConfs) {
                                    String mappedDependency = evaluateConditions
                                        ? evaluateCondition(depConf.trim(), dd)
                                        : depConf.trim();
                                    if (mappedDependency != null) {
                                        dd.addDependencyConfiguration(modConf.trim(),
                                            mappedDependency);
                                    }
                                }
                            } else {
                                // no default mapping found for this configuration, map
                                // configuration to itself
                                dd.addDependencyConfiguration(modConf.trim(), modConf
                                    .trim());
                            }
                        }
                    }
                } else if (ops.length == 2) {
                    String[] modConfs = ops[0].split(",");
                    String[] depConfs = ops[1].split(",");
                    for (String modConf : modConfs) {
                        for (String depConf : depConfs) {
                            String mappedDependency = evaluateConditions ? evaluateCondition(
                                depConf.trim(), dd) : depConf.trim();
                            if (mappedDependency != null) {
                                dd.addDependencyConfiguration(modConf.trim(), mappedDependency);
                            }
                        }
                    }
                } else {
                    addError("invalid conf " + s + " for " + dd);
                }
            }

            if (md.isMappingOverride()) {
                addExtendingConfigurations(conf, dd, useDefaultMappingToGuessRightOperand);
            }
        }

        /**
         * Evaluate the optional condition in the given configuration, like "[org=MYORG]confX". If
         * the condition evaluates to true, the configuration is returned, if the condition
         * evaluate to false, null is returned. If there are no conditions, the configuration
         * itself is returned.
         *
         * @param conf
         *            the configuration to evaluate
         * @param dd
         *            the dependencydescriptor to which the configuration will be added
         * @return the evaluated condition
         */
        private String evaluateCondition(String conf, DefaultDependencyDescriptor dd) {
            if (conf.charAt(0) != '[') {
                return conf;
            }

            int endConditionIndex = conf.indexOf(']');
            if (endConditionIndex == -1) {
                addError("invalid conf " + conf + " for " + dd);
                return null;
            }

            String condition = conf.substring(1, endConditionIndex);

            int notEqualIndex = condition.indexOf("!=");
            if (notEqualIndex == -1) {
                int equalIndex = condition.indexOf('=');
                if (equalIndex == -1) {
                    addError("invalid conf " + conf + " for " + dd.getDependencyRevisionId());
                    return null;
                }

                String leftOp = condition.substring(0, equalIndex).trim();
                String rightOp = condition.substring(equalIndex + 1).trim();

                // allow organisation synonyms, like 'org' or 'organization'
                if (leftOp.equals("org") || leftOp.equals("organization")) {
                    leftOp = "organisation";
                }

                String attrValue = dd.getAttribute(leftOp);
                if (!rightOp.equals(attrValue)) {
                    return null;
                }
            } else {
                String leftOp = condition.substring(0, notEqualIndex).trim();
                String rightOp = condition.substring(notEqualIndex + 2).trim();

                // allow organisation synonyms, like 'org' or 'organization'
                if (leftOp.equals("org") || leftOp.equals("organization")) {
                    leftOp = "organisation";
                }

                String attrValue = dd.getAttribute(leftOp);
                if (rightOp.equals(attrValue)) {
                    return null;
                }
            }

            return conf.substring(endConditionIndex + 1);
        }

        private void addExtendingConfigurations(String[] confs, DefaultDependencyDescriptor dd,
                boolean useDefaultMappingToGuessRightOperand) {
            for (String conf : confs) {
                addExtendingConfigurations(conf, dd, useDefaultMappingToGuessRightOperand);
            }
        }

        private void addExtendingConfigurations(String conf, DefaultDependencyDescriptor dd,
                boolean useDefaultMappingToGuessRightOperand) {
            Set<String> configsToAdd = new HashSet<>();
            Configuration[] configs = md.getConfigurations();
            for (Configuration config : configs) {
                String[] ext = config.getExtends();
                for (String s : ext) {
                    if (conf.equals(s)) {
                        String configName = config.getName();
                        configsToAdd.add(configName);
                        addExtendingConfigurations(configName, dd,
                            useDefaultMappingToGuessRightOperand);
                    }
                }
            }

            String[] confs = configsToAdd.toArray(new String[0]);
            parseDepsConfs(confs, dd, useDefaultMappingToGuessRightOperand);
        }

        protected DependencyDescriptor getDefaultConfMappingDescriptor() {
            if (defaultConfMappingDescriptor == null) {
                defaultConfMappingDescriptor = new DefaultDependencyDescriptor(createModuleRevisionId("", "", ""), false);
                parseDepsConfs(defaultConfMapping, defaultConfMappingDescriptor, false, false);
            }
            return defaultConfMappingDescriptor;
        }

        protected void addError(String msg) {
            errors.add(msg + " in " + res.getDisplayName());
        }

        @Override
        public void warning(SAXParseException ex) {
            LOGGER.warn("xml parsing: {}: {}", getLocationString(ex), ex.getMessage());
        }

        @Override
        public void error(SAXParseException ex) {
            addError("xml parsing: " + getLocationString(ex) + ": " + ex.getMessage());
        }

        @Override
        public void fatalError(SAXParseException ex) {
            addError("[Fatal Error] " + getLocationString(ex) + ": " + ex.getMessage());
        }

        /** Returns a string of the location. */
        private String getLocationString(SAXParseException ex) {
            StringBuilder str = new StringBuilder();

            String systemId = ex.getSystemId();
            if (systemId != null) {
                int index = systemId.lastIndexOf('/');
                if (index != -1) {
                    systemId = systemId.substring(index + 1);
                }
                str.append(systemId);
            } else {
                str.append(getResource().getDisplayName());
            }
            str.append(':');
            str.append(ex.getLineNumber());
            str.append(':');
            str.append(ex.getColumnNumber());

            return str.toString();

        } // getLocationString(SAXParseException):String

        protected String getDefaultConf() {
            return defaultConf != null ? defaultConf
                    : (defaultConfMapping != null ? defaultConfMapping : DEFAULT_CONF_MAPPING);
        }

        protected void setDefaultConf(String defaultConf) {
            this.defaultConf = defaultConf;
        }

        public DefaultModuleDescriptor getModuleDescriptor() throws ParseException {
            checkErrors();
            return md;
        }

        public MutableIvyModuleResolveMetadata getMetaData() {
            return metaData.build();
        }

        private void replaceConfigurationWildcards(ModuleDescriptor md) {
            Configuration[] configs = md.getConfigurations();
            for (Configuration config : configs) {
                config.replaceWildcards(md);
            }
        }

        protected DefaultModuleDescriptor getMd() {
            return md;
        }
    }

    public static class Parser extends AbstractParser implements LexicalHandler {
        public enum State {
            NONE,
            INFO,
            CONF,
            PUB,
            DEP,
            DEP_ARTIFACT,
            ARTIFACT_INCLUDE,
            ARTIFACT_EXCLUDE,
            CONFLICT,
            EXCLUDE,
            DEPS,
            DESCRIPTION,
            EXTRA_INFO
        }

        private static final List<String> ALLOWED_VERSIONS = Arrays.asList("1.0", "1.1", "1.2", "1.3", "1.4", "2.0", "2.1", "2.2");

        /* how and what do we have to parse */
        private final DescriptorParseContext parseContext;
        private final RelativeUrlResolver relativeUrlResolver = new NormalRelativeUrlResolver();
        private final URL descriptorURL;
        private final IvyModuleDescriptorConverter moduleDescriptorConverter;
        private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;
        private final IvyMutableModuleMetadataFactory metadataFactory;

        private boolean validate = true;

        /* Parsing state */
        private State state = State.NONE;
        private PatternMatcher defaultMatcher;
        private DefaultDependencyDescriptor dd;
        private ConfigurationAware confAware;
        private BuildableIvyArtifact artifact;
        private String conf;
        private boolean artifactsDeclared;
        private StringBuffer buffer;
        private String descriptorVersion;
        private String[] publicationsDefaultConf;
        private boolean hasGradleMetadataRedirect;
        final Map<String, String> properties;

        public Parser(DescriptorParseContext parseContext, IvyModuleDescriptorConverter moduleDescriptorConverter, ExternalResource res, URL descriptorURL, ImmutableModuleIdentifierFactory moduleIdentifierFactory, IvyMutableModuleMetadataFactory metadataFactory, Map<String, String> properties) {
            super(res);
            this.parseContext = parseContext;
            this.moduleDescriptorConverter = moduleDescriptorConverter;
            this.descriptorURL = descriptorURL;
            this.metadataFactory = metadataFactory;
            this.properties = properties;
            this.moduleIdentifierFactory = moduleIdentifierFactory;
        }

        public Parser newParser(ExternalResource res, URL descriptorURL) {
            Parser parser = new Parser(parseContext, moduleDescriptorConverter, res, descriptorURL, moduleIdentifierFactory, metadataFactory, properties);
            parser.setValidate(validate);
            return parser;
        }

        public void setValidate(boolean validate) {
            this.validate = validate;
        }

        public boolean isValidate() {
            return validate;
        }

        public DescriptorParseContext getParseContext() {
            return parseContext;
        }

        public void parse() throws ParseException {
            getResource().withContent(inputStream -> {
                URL schemaURL = validate ? getSchemaURL() : null;
                InputSource inSrc = new InputSource(inputStream);
                inSrc.setSystemId(descriptorURL.toExternalForm());
                try {
                    ParserHelper.parse(inSrc, schemaURL, Parser.this);
                } catch (Exception e) {
                    throw new MetaDataParseException("Ivy file", getResource(), e);
                }
            });
            checkErrors();
            maybeAddDefaultConfiguration();
            replaceConfigurationWildcards();
            maybeAddDefaultArtifact();
            validateConfigurations();
            validateArtifacts();
            validateExcludes();
            getMd().check();
        }

        private void validateConfigurations() {
            for (Configuration configuration : getMd().getConfigurations()) {
                for (String parent : configuration.getExtends()) {
                    if (getMd().getConfiguration(parent) == null) {
                        throw new IllegalArgumentException("Configuration '" + configuration.getName() + "' extends configuration '" + parent + "' which is not declared.");
                    }
                }
            }
        }

        private void validateExcludes() {
            for (ExcludeRule excludeRule : getMd().getAllExcludeRules()) {
                for (String conf : excludeRule.getConfigurations()) {
                    if (getMd().getConfiguration(conf) == null) {
                        throw new IllegalArgumentException("Exclude rule " + excludeRule.getId() + " is mapped to configuration '" + conf + "' which is not declared.");
                    }
                }
            }
        }

        private void validateArtifacts() {
            for (Artifact artifact : metaData.getArtifacts()) {
                for (String conf : artifact.getConfigurations()) {
                    if (getMd().getConfiguration(conf) == null) {
                        throw new IllegalArgumentException("Artifact " + artifact.getArtifactName() + " is mapped to configuration '" + conf + "' which is not declared.");
                    }
                }
            }
        }

        private void maybeAddDefaultArtifact() {
            if (!artifactsDeclared) {
                IvyArtifactName implicitArtifact = new DefaultIvyArtifactName(getMd().getModuleRevisionId().getName(), "jar", "jar");
                Set<String> configurationNames = Sets.newHashSet(getMd().getConfigurationsNames());
                metaData.addArtifact(implicitArtifact, configurationNames);
            }
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes)
                throws SAXException {
            try {
                if (state == State.DESCRIPTION) {
                    // make sure we don't interpret any tag while in description tag
                    descriptionStarted(qName, attributes);
                } else if ("ivy-module".equals(qName)) {
                    ivyModuleStarted(attributes);
                } else if ("info".equals(qName)) {
                    infoStarted(attributes);
                } else if (state == State.INFO && "extends".equals(qName)) {
                    extendsStarted(attributes);
                } else if (state == State.INFO && "license".equals(qName)) {
                    getMd().addLicense(new License(substitute(attributes.getValue("name")), substitute(attributes.getValue("url"))));
                } else if (state == State.INFO && "ivyauthor".equals(qName)) {
                    // nothing to do, we don't store this
                    return;
                } else if (state == State.INFO && "repository".equals(qName)) {
                    // nothing to do, we don't store this
                    return;
                } else if (state == State.INFO && "description".equals(qName)) {
                    getMd().setHomePage(substitute(attributes.getValue("homepage")));
                    state = State.DESCRIPTION;
                    buffer = new StringBuffer();
                } else if (state == State.INFO && isOtherNamespace(qName)) {
                    buffer = new StringBuffer();
                    state = State.EXTRA_INFO;
                } else if ("configurations".equals(qName)) {
                    configurationStarted(attributes);
                } else if ("publications".equals(qName)) {
                    publicationsStarted(attributes);
                } else if ("dependencies".equals(qName)) {
                    dependenciesStarted(attributes);
                } else if ("conflicts".equals(qName)) {
                    state = State.CONFLICT;
                    maybeAddDefaultConfiguration();
                } else if ("artifact".equals(qName)) {
                    artifactStarted(qName, attributes);
                } else if ("include".equals(qName) && state == State.DEP) {
                    addIncludeRule(qName, attributes);
                } else if ("exclude".equals(qName) && state == State.DEP) {
                    addExcludeRule(qName, attributes);
                } else if ("exclude".equals(qName) && state == State.DEPS) {
                    state = State.EXCLUDE;
                    parseRule(qName, attributes);
                    getMd().addExcludeRule((ExcludeRule) confAware);
                } else if ("dependency".equals(qName)) {
                    dependencyStarted(attributes);
                } else if ("conf".equals(qName)) {
                    confStarted(attributes);
                } else if ("mapped".equals(qName)) {
                    dd.addDependencyConfiguration(conf, substitute(attributes.getValue("name")));
                } else if (("conflict".equals(qName) && state == State.DEPS) || "manager".equals(qName) && state == State.CONFLICT) {
                    LOGGER.debug("Ivy.xml conflict managers are not supported by Gradle. Ignoring conflict manager declared in {}", getResource().getDisplayName());
                } else if ("override".equals(qName) && state == State.DEPS) {
                    LOGGER.debug("Ivy.xml dependency overrides are not supported by Gradle. Ignoring override declared in {}", getResource().getDisplayName());
                } else if ("include".equals(qName) && state == State.CONF) {
                    includeConfStarted(attributes);
                } else if (validate && state != State.EXTRA_INFO && state != State.DESCRIPTION) {
                    addError("unknown tag " + qName);
                }
            } catch (Exception ex) {
                if (ex instanceof SAXException) {
                    throw (SAXException) ex;
                }
                SAXException sax = new SAXException("Problem occurred while parsing ivy file: "
                        + ex.getMessage(), ex);
                sax.initCause(ex);
                throw sax;
            }
        }

        private void extendsStarted(Attributes attributes) throws ParseException {
            String parentOrganisation = attributes.getValue("organisation");
            String parentModule = attributes.getValue("module");
            String parentRevision = attributes.getValue("revision");
            String location = elvis(attributes.getValue("location"), "../ivy.xml");

            String extendType = elvis(attributes.getValue("extendType"), "all").toLowerCase();
            List<String> extendTypes = Arrays.asList(extendType.split(","));

            ModuleDescriptor parent;
            try {
                LOGGER.debug("Trying to parse included ivy file :{}", location);
                parent = parseOtherIvyFileOnFileSystem(location);
                if (parent != null) {
                    //verify that the parsed descriptor is the correct parent module.
                    ModuleId expected = IvyUtil.createModuleId(parentOrganisation, parentModule);
                    ModuleId pid = parent.getModuleRevisionId().getModuleId();
                    if (!expected.equals(pid)) {
                        LOGGER.warn("Ignoring parent Ivy file {}; expected {} but found {}", location, expected, pid);
                        parent = null;
                    }
                }

                // if the included ivy file is not found on file system, tries to resolve using
                // repositories
                if (parent == null) {
                    LOGGER.debug("Trying to parse included ivy file by asking repository for module :{}#{};{}",
                        parentOrganisation, parentModule, parentRevision);
                    parent = parseOtherIvyFile(parentOrganisation, parentModule, parentRevision);
                }
            } catch(Exception e) {
                throw (ParseException) new ParseException("Unable to parse included ivy file for " + parentOrganisation + "#" + parentModule + ";" + parentRevision, 0).initCause(e);
            }

            if (parent == null) {
                throw new ParseException("Unable to parse included ivy file for " + parentOrganisation + "#" + parentModule + ";" + parentRevision, 0);
            }

            mergeWithOtherModuleDescriptor(extendTypes, parent);
        }

        private void mergeWithOtherModuleDescriptor(List<String> extendTypes, ModuleDescriptor parent) {

            if (extendTypes.contains("all")) {
                mergeAll(parent);
            } else {
                if (extendTypes.contains("info")) {
                    mergeInfo(parent);
                }

                if (extendTypes.contains("configurations")) {
                    mergeConfigurations(parent.getModuleRevisionId(), parent.getConfigurations());
                }

                if (extendTypes.contains("dependencies")) {
                    mergeDependencies(parent.getDependencies());
                }

                if (extendTypes.contains("description")) {
                    mergeDescription(parent.getDescription());
                }
            }
        }

        private void mergeAll(ModuleDescriptor parent) {
            ModuleRevisionId sourceMrid = parent.getModuleRevisionId();
            mergeInfo(parent);
            mergeConfigurations(sourceMrid, parent.getConfigurations());
            mergeDependencies(parent.getDependencies());
            mergeDescription(parent.getDescription());
        }

        @SuppressWarnings("unchecked")
        private void mergeInfo(ModuleDescriptor parent) {
            ModuleRevisionId parentMrid = parent.getModuleRevisionId();

            DefaultModuleDescriptor descriptor = getMd();
            ModuleRevisionId currentMrid = descriptor.getModuleRevisionId();

            ModuleRevisionId mergedMrid = createModuleRevisionId(
                    mergeValue(parentMrid.getOrganisation(), currentMrid.getOrganisation()),
                    currentMrid.getName(),
                    mergeValue(parentMrid.getBranch(), currentMrid.getBranch()),
                    mergeValue(parentMrid.getRevision(), currentMrid.getRevision()),
                    mergeValues(parentMrid.getQualifiedExtraAttributes(), currentMrid.getQualifiedExtraAttributes())
            );

            descriptor.setModuleRevisionId(mergedMrid);
            descriptor.setResolvedModuleRevisionId(mergedMrid);

            descriptor.setStatus(mergeValue(parent.getStatus(), descriptor.getStatus()));
            if (descriptor.getNamespace() == null && parent instanceof DefaultModuleDescriptor) {
                Namespace parentNamespace = ((DefaultModuleDescriptor) parent).getNamespace();
                descriptor.setNamespace(parentNamespace);
            }
        }

        private static String mergeValue(String inherited, String override) {
            return override == null ? inherited : override;
        }

        private static Map<String, String> mergeValues(Map<String, String> inherited, Map<String, String> overrides) {
            LinkedHashMap<String, String> dup = new LinkedHashMap<>(inherited.size() + overrides.size());
            dup.putAll(inherited);
            dup.putAll(overrides);
            return dup;
        }

        private void mergeConfigurations(ModuleRevisionId sourceMrid, Configuration[] configurations) {
            DefaultModuleDescriptor md = getMd();
            for (Configuration configuration : configurations) {
                LOGGER.debug("Merging configuration with: {}", configuration.getName());
                //copy configuration from parent descriptor
                md.addConfiguration(new Configuration(configuration, sourceMrid));
            }
        }

        private void mergeDependencies(DependencyDescriptor[] dependencies) {
            DefaultModuleDescriptor md = getMd();
            for (DependencyDescriptor dependencyDescriptor : dependencies) {
                LOGGER.debug("Merging dependency with: {}", dependencyDescriptor.getDependencyRevisionId());
                md.addDependency(dependencyDescriptor);
            }
        }

        private void mergeDescription(String description) {
            String current = getMd().getDescription();
            if (current == null || current.trim().length() == 0) {
                getMd().setDescription(description);
            }
        }

        private ModuleDescriptor parseOtherIvyFileOnFileSystem(String location)
                throws ParseException, IOException {
            URL url = relativeUrlResolver.getURL(descriptorURL, location);
            LOGGER.debug("Trying to load included ivy file from {}", url);
            ExternalResource resource = UrlExternalResource.open(url);
            try {
                return parseModuleDescriptor(resource, url);
            } catch (MissingResourceException e) {
                // Ignore
                return null;
            }
        }

        protected ModuleDescriptor parseOtherIvyFile(String parentOrganisation, String parentModule, String parentRevision) throws IOException, ParseException, SAXException {
            ModuleComponentIdentifier importedId = DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId(parentOrganisation, parentModule), parentRevision);
            LocallyAvailableExternalResource externalResource = parseContext.getMetaDataArtifact(importedId, ArtifactType.IVY_DESCRIPTOR);

            return parseModuleDescriptor(externalResource, externalResource.getFile().toURI().toURL());
        }

        private ModuleDescriptor parseModuleDescriptor(ExternalResource externalResource, URL descriptorURL) throws ParseException {
            Parser parser = newParser(externalResource, descriptorURL);
            parser.parse();
            return parser.getModuleDescriptor();
        }

        private void publicationsStarted(Attributes attributes) {
            state = State.PUB;
            artifactsDeclared = true;
            maybeAddDefaultConfiguration();
            String defaultConf = substitute(attributes.getValue("defaultconf"));
            if (defaultConf != null) {
                this.publicationsDefaultConf = defaultConf.split(",");
            }
        }

        private boolean isOtherNamespace(String qName) {
            return qName.indexOf(':') != -1;
        }

        private void includeConfStarted(Attributes attributes)
                throws SAXException, IOException, ParserConfigurationException, ParseException {
            URL url = relativeUrlResolver.getURL(descriptorURL, substitute(attributes.getValue("file")), substitute(attributes.getValue("url")));
            if (url == null) {
                throw new SAXException("include tag must have a file or an url attribute");
            }

            // create a new temporary parser to read the configurations from
            // the specified file.
            Parser parser = newParser(UrlExternalResource.open(url), url);
            ParserHelper.parse(url, null, parser);

            // add the configurations from this temporary parser to this module descriptor
            Configuration[] configs = parser.getModuleDescriptor().getConfigurations();
            for (Configuration config : configs) {
                getMd().addConfiguration(config);
            }
            if (parser.getDefaultConfMapping() != null) {
                LOGGER.debug("setting default conf mapping from imported configurations file: {}", parser.getDefaultConfMapping());
                setDefaultConfMapping(parser.getDefaultConfMapping());
            }
            if (parser.getDefaultConf() != null) {
                LOGGER.debug("setting default conf from imported configurations file: {}", parser.getDefaultConf());
                setDefaultConf(parser.getDefaultConf());
            }
            if (parser.getMd().isMappingOverride()) {
                LOGGER.debug("enabling mapping-override from imported configurations file");
                getMd().setMappingOverride(true);
            }
        }

        private void confStarted(Attributes attributes) {
            String conf = substitute(attributes.getValue("name"));
            switch (state) {
                case CONF:
                    Configuration.Visibility visibility = Configuration.Visibility.getVisibility(elvis(substitute(attributes.getValue("visibility")), "public"));
                    String description = substitute(attributes.getValue("description"));
                    String[] extend = substitute(attributes.getValue("extends")) == null ? null : substitute(attributes.getValue("extends")).split(",");
                    String transitiveValue = attributes.getValue("transitive");
                    boolean transitive = (transitiveValue == null) || Boolean.parseBoolean(attributes.getValue("transitive"));
                    String deprecated = attributes.getValue("deprecated");
                    Configuration configuration = new Configuration(conf, visibility, description, extend, transitive, deprecated);
                    fillExtraAttributes(configuration, attributes,
                            new String[]{"name", "visibility", "extends", "transitive", "description", "deprecated"});
                    getMd().addConfiguration(configuration);
                    break;
                case PUB:
                    if ("*".equals(conf)) {
                        String[] confs = getMd().getConfigurationsNames();
                        for (String confName : confs) {
                            artifact.addConfiguration(confName);
                        }
                    } else {
                        artifact.addConfiguration(conf);
                    }
                    break;
                case DEP:
                    this.conf = conf;
                    String mappeds = substitute(attributes.getValue("mapped"));
                    if (mappeds != null) {
                        String[] mapped = mappeds.split(",");
                        for (String depConf : mapped) {
                            dd.addDependencyConfiguration(conf, depConf.trim());
                        }
                    }
                    break;
                case DEP_ARTIFACT:
                case ARTIFACT_INCLUDE:
                case ARTIFACT_EXCLUDE:
                    addConfiguration(conf);
                    break;
                default:
                    if (validate) {
                        addError("conf tag found in invalid tag: " + state);
                    }
                    break;
            }
        }

        private void dependencyStarted(Attributes attributes) {
            state = State.DEP;
            String org = substitute(attributes.getValue("org"));
            if (org == null) {
                org = getMd().getModuleRevisionId().getOrganisation();
            }
            boolean force = Boolean.parseBoolean(substitute(attributes.getValue("force")));
            boolean changing = Boolean.parseBoolean(substitute(attributes.getValue("changing")));

            String transitiveValue = substitute(attributes.getValue("transitive"));
            boolean transitive = (transitiveValue == null) || Boolean.parseBoolean(transitiveValue);

            String name = substitute(attributes.getValue("name"));
            String branch = substitute(attributes.getValue("branch"));
            String branchConstraint = substitute(attributes.getValue("branchConstraint"));
            String rev = substitute(attributes.getValue("rev"));
            String revConstraint = substitute(attributes.getValue("revConstraint"));

            Map<String, String> extraAttributes = getExtraAttributes(attributes, DEPENDENCY_REGULAR_ATTRIBUTES);

            ModuleRevisionId revId = createModuleRevisionId(org, name, branch, rev, extraAttributes);
            ModuleRevisionId dynamicId;
            if ((revConstraint == null) && (branchConstraint == null)) {
                // no dynamic constraints defined, so dynamicId equals revId
                dynamicId = createModuleRevisionId(org, name, branch, rev, extraAttributes, false);
            } else {
                if (branchConstraint == null) {
                    // this situation occurs when there was no branch defined
                    // in the original dependency descriptor. So the dynamicId
                    // shouldn't contain a branch neither
                    dynamicId = createModuleRevisionId(org, name, null, revConstraint, extraAttributes, false);
                } else {
                    dynamicId = createModuleRevisionId(org, name, branchConstraint, revConstraint, extraAttributes);
                }
            }

            dd = new DefaultDependencyDescriptor(getMd(), revId, dynamicId, force, changing, transitive);
            getMd().addDependency(dd);
            String confs = substitute(attributes.getValue("conf"));
            if (confs != null && confs.length() > 0) {
                parseDepsConfs(confs, dd);
            }
        }

        private void artifactStarted(String qName, Attributes attributes)
                throws MalformedURLException {
            if (state == State.PUB) {
                // this is a published artifact
                String artName = elvis(substitute(attributes.getValue("name")), getMd().getModuleRevisionId().getName());
                String type = elvis(substitute(attributes.getValue("type")), "jar");
                String ext = elvis(substitute(attributes.getValue("ext")), type);
                String classifier = readClassifierAttribute(attributes);
                artifact = new BuildableIvyArtifact(artName, type, ext, classifier);
                String confs = substitute(attributes.getValue("conf"));

                // Only add confs if they are specified. if they aren't, endElement will handle this only if there are no conf defined in sub elements
                if (confs != null && confs.length() > 0) {
                    String[] conf;
                    if ("*".equals(confs)) {
                        conf = getMd().getConfigurationsNames();
                    } else {
                        conf = confs.split(",");
                    }
                    for (String confName : conf) {
                        artifact.addConfiguration(confName.trim());
                    }
                }
            } else if (state == State.DEP) {
                // this is an artifact asked for a particular dependency
                addDependencyArtifacts(qName, attributes);
            } else if (validate) {
                addError("artifact tag found in invalid tag: " + state);
            }
        }

        /**
         * Handle the 'classifier' attribute in any namespace: different tools publish differently.
         */
        private String readClassifierAttribute(Attributes attributes) {
            for (int i = 0; i < attributes.getLength(); i++) {
                if (attributes.getLocalName(i).equals("classifier")) {
                    return attributes.getValue(i);
                }
            }
            return null;
        }

        private void dependenciesStarted(Attributes attributes) {
            state = State.DEPS;
            String defaultConf = substitute(attributes.getValue("defaultconf"));
            if (defaultConf != null) {
                setDefaultConf(defaultConf);
            }
            String defaultConfMapping = substitute(attributes.getValue("defaultconfmapping"));
            if (defaultConfMapping != null) {
                setDefaultConfMapping(defaultConfMapping);
            }
            String confMappingOverride = substitute(attributes.getValue("confmappingoverride"));
            if (confMappingOverride != null) {
                getMd().setMappingOverride(Boolean.parseBoolean(confMappingOverride));
            }
            maybeAddDefaultConfiguration();
        }

        private void configurationStarted(Attributes attributes) {
            state = State.CONF;
            setDefaultConfMapping(substitute(attributes.getValue("defaultconfmapping")));
            setDefaultConf(substitute(attributes.getValue("defaultconf")));
            getMd().setMappingOverride(Boolean.parseBoolean(substitute(attributes.getValue("confmappingoverride"))));
        }

        private void infoStarted(Attributes attributes) {
            state = State.INFO;
            String org = substitute(attributes.getValue("organisation"));
            String module = substitute(attributes.getValue("module"));
            String revision = substitute(attributes.getValue("revision"));
            String branch = substitute(attributes.getValue("branch"));
            Map<String, String> extraAttributes = getExtraAttributes(attributes, new String[]{"organisation", "module", "revision", "status", "publication", "branch", "namespace", "default", "resolver"});
            getMd().setModuleRevisionId(createModuleRevisionId(org, module, branch, revision, extraAttributes));

            getMd().setStatus(elvis(substitute(attributes.getValue("status")), "integration"));
            getMd().setDefault(Boolean.parseBoolean(substitute(attributes.getValue("default"))));
            String pubDate = substitute(attributes.getValue("publication"));
            if (pubDate != null && pubDate.length() > 0) {
                try {
                    final SimpleDateFormat ivyDateFormat = new SimpleDateFormat(IVY_DATE_FORMAT_PATTERN);
                    getMd().setPublicationDate(ivyDateFormat.parse(pubDate));
                } catch (ParseException e) {
                    addError("invalid publication date format: " + pubDate);
                }
            }
        }

        private void ivyModuleStarted(Attributes attributes) throws SAXException {
            descriptorVersion = attributes.getValue("version");
            int versionIndex = ALLOWED_VERSIONS.indexOf(descriptorVersion);
            if (versionIndex == -1) {
                addError("invalid version " + descriptorVersion);
                throw new SAXException("invalid version " + descriptorVersion);
            }
            if (versionIndex >= ALLOWED_VERSIONS.indexOf("1.3")) {
                LOGGER.debug("post 1.3 ivy file: using {} as default matcher", PatternMatcher.EXACT);
                defaultMatcher = getMatcher(PatternMatcher.EXACT);
            } else {
                LOGGER.debug("pre 1.3 ivy file: using {} as default matcher", PatternMatcher.EXACT_OR_REGEXP);
                defaultMatcher = getMatcher(PatternMatcher.EXACT_OR_REGEXP);
            }

            for (int i = 0; i < attributes.getLength(); i++) {
                if (attributes.getQName(i).startsWith("xmlns:")) {
                    getMd().addExtraAttributeNamespace(attributes.getQName(i).substring("xmlns:".length()), attributes.getValue(i));
                }
            }
        }

        private void descriptionStarted(String qName, Attributes attributes) {
            buffer.append("<").append(qName);
            for (int i = 0; i < attributes.getLength(); i++) {
                buffer.append(" ");
                buffer.append(attributes.getQName(i));
                buffer.append("=\"");
                buffer.append(attributes.getValue(i));
                buffer.append("\"");
            }
            buffer.append(">");
        }

        private void addDependencyArtifacts(String tag, Attributes attributes)
                throws MalformedURLException {
            state = State.DEP_ARTIFACT;
            parseRule(tag, attributes);
        }

        private void addIncludeRule(String tag, Attributes attributes)
                throws MalformedURLException {
            state = State.ARTIFACT_INCLUDE;
            parseRule(tag, attributes);
        }

        private void addExcludeRule(String tag, Attributes attributes)
                throws MalformedURLException {
            state = State.ARTIFACT_EXCLUDE;
            parseRule(tag, attributes);
        }

        private void parseRule(String tag, Attributes attributes) throws MalformedURLException {
            String name = substitute(attributes.getValue("name"));
            if (name == null) {
                name = substitute(attributes.getValue("artifact"));
                if (name == null) {
                    name = "artifact".equals(tag) ? dd.getDependencyId().getName()
                            : PatternMatchers.ANY_EXPRESSION;
                }
            }
            String type = substitute(attributes.getValue("type"));
            if (type == null) {
                type = "artifact".equals(tag) ? "jar" : PatternMatchers.ANY_EXPRESSION;
            }
            String ext = substitute(attributes.getValue("ext"));
            ext = ext != null ? ext : type;
            if (state == State.DEP_ARTIFACT) {
                String url = substitute(attributes.getValue("url"));
                Map<String, String> extraAttributes = getExtraAttributes(attributes, new String[]{"name", "type", "ext", "url", "conf"});
                confAware = new DefaultDependencyArtifactDescriptor(dd, name, type, ext, url == null ? null : new URL(url), extraAttributes);
            } else if (state == State.ARTIFACT_INCLUDE) {
                PatternMatcher matcher = getPatternMatcher(attributes.getValue("matcher"));
                String org = elvis(substitute(attributes.getValue("org")), PatternMatchers.ANY_EXPRESSION);
                String module = elvis(substitute(attributes.getValue("module")), PatternMatchers.ANY_EXPRESSION);
                ArtifactId aid = new ArtifactId(IvyUtil.createModuleId(org, module), name, type, ext);
                Map<String, String> extraAttributes = getExtraAttributes(attributes, new String[]{"org", "module", "name", "type", "ext", "matcher", "conf"});
                confAware = new DefaultIncludeRule(aid, matcher, extraAttributes);
            } else { // _state == ARTIFACT_EXCLUDE || EXCLUDE
                PatternMatcher matcher = getPatternMatcher(attributes.getValue("matcher"));
                String org = elvis(substitute(attributes.getValue("org")), PatternMatchers.ANY_EXPRESSION);
                String module = elvis(substitute(attributes.getValue("module")), PatternMatchers.ANY_EXPRESSION);
                ArtifactId aid = new ArtifactId(IvyUtil.createModuleId(org, module), name, type, ext);
                Map<String, String> extraAttributes = getExtraAttributes(attributes, new String[]{"org", "module", "name", "type", "ext", "matcher", "conf"});
                confAware = new DefaultExcludeRule(aid, matcher, extraAttributes);
            }
            String confs = substitute(attributes.getValue("conf"));
            // only add confs if they are specified. if they aren't, endElement will handle this
            // only if there are no conf defined in sub elements
            if (confs != null && confs.length() > 0) {
                String[] conf;
                if ("*".equals(confs)) {
                    conf = getMd().getConfigurationsNames();
                } else {
                    conf = confs.split(",");
                }
                for (String confName : conf) {
                    addConfiguration(confName.trim());
                }
            }
        }

        private void addConfiguration(String c) {
            confAware.addConfiguration(c);
            if (state != State.EXCLUDE) {
                // we are currently adding a configuration to either an include, exclude or artifact
                // element
                // of a dependency. This means that we have to add this element to the corresponding
                // conf
                // of the current dependency descriptor
                if (confAware instanceof DependencyArtifactDescriptor) {
                    dd.addDependencyArtifact(c, (DependencyArtifactDescriptor) confAware);
                } else if (confAware instanceof IncludeRule) {
                    dd.addIncludeRule(c, (IncludeRule) confAware);
                } else if (confAware instanceof ExcludeRule) {
                    dd.addExcludeRule(c, (ExcludeRule) confAware);
                }
            }
        }

        private PatternMatcher getPatternMatcher(String m) {
            String matcherName = substitute(m);
            PatternMatcher matcher = matcherName == null ? defaultMatcher : getMatcher(matcherName);
            if (matcher == null) {
                throw new IllegalArgumentException("unknown matcher " + matcherName);
            }
            return matcher;
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            if (buffer != null) {
                buffer.append(ch, start, length);
            }
        }

        @Override
        @SuppressWarnings({"deprecation"})
        public void endElement(String uri, String localName, String qName) {
            if (state == State.PUB && "artifact".equals(qName)) {
                if (artifact.getConfigurations().isEmpty()) {
                    String[] confs = publicationsDefaultConf == null ? getMd().getConfigurationsNames() : publicationsDefaultConf;
                    for (String confName : confs) {
                        artifact.addConfiguration(confName.trim());
                    }
                }
                metaData.addArtifact(artifact.getArtifact(), artifact.getConfigurations());
                artifact = null;
            } else if ("configurations".equals(qName)) {
                maybeAddDefaultConfiguration();
            } else if ((state == State.DEP_ARTIFACT && "artifact".equals(qName))
                    || (state == State.ARTIFACT_INCLUDE && "include".equals(qName))
                    || (state == State.ARTIFACT_EXCLUDE && "exclude".equals(qName))) {
                state = State.DEP;
                if (confAware.getConfigurations().length == 0) {
                    String[] confs = getMd().getConfigurationsNames();
                    for (String confName : confs) {
                        addConfiguration(confName);
                    }
                }
                confAware = null;
            } else if ("exclude".equals(qName) && state == State.EXCLUDE) {
                if (confAware.getConfigurations().length == 0) {
                    String[] confs = getMd().getConfigurationsNames();
                    for (String confName : confs) {
                        addConfiguration(confName);
                    }
                }
                confAware = null;
                state = State.DEPS;
            } else if ("dependency".equals(qName) && state == State.DEP) {
                if (dd.getModuleConfigurations().length == 0) {
                    parseDepsConfs(getDefaultConf(), dd);
                }
                state = State.DEPS;
            } else if ("dependencies".equals(qName) && state == State.DEPS) {
                state = State.NONE;
            } else if (state == State.INFO && "info".equals(qName)) {
                metaData = new IvyModuleResolveMetaDataBuilder(getMd(), moduleDescriptorConverter, metadataFactory);
                state = State.NONE;
            } else if (state == State.DESCRIPTION && "description".equals(qName)) {
                getMd().setDescription(buffer == null ? "" : buffer.toString().trim());
                buffer = null;
                state = State.INFO;
            } else if (state == State.EXTRA_INFO) {
                getMd().getExtraInfo().put(new NamespaceId(uri, localName).encode(), buffer == null ? "" : buffer.toString());
                buffer = null;
                state = State.INFO;
            } else if (state == State.DESCRIPTION) {
                if (buffer.toString().endsWith("<" + qName + ">")) {
                    buffer.deleteCharAt(buffer.length() - 1);
                    buffer.append("/>");
                } else {
                    buffer.append("</").append(qName).append(">");
                }
            }
        }

        private void maybeAddDefaultConfiguration() {
            if (getMd().getConfigurations().length == 0) {
                getMd().addConfiguration(new Configuration("default"));
            }
        }

        private void replaceConfigurationWildcards() {
            Configuration[] configs = getMd().getConfigurations();
            for (Configuration config : configs) {
                config.replaceWildcards(getMd());
            }
        }

        private URL getSchemaURL() {
            URL resource = getClass().getClassLoader().getResource("org/apache/ivy/plugins/parser/xml/ivy.xsd");
            assert resource != null;
            return resource;
        }

        private String elvis(String value, String defaultValue) {
            return value != null ? value : defaultValue;
        }

        private String substitute(String value) {
            return IvyPatternHelper.substituteVariables(value, properties);
        }

        private Map<String, String> getExtraAttributes(Attributes attributes, String[] ignoredAttributeNames) {
            Map<String, String> ret = new HashMap<>();
            Collection<String> ignored = Arrays.asList(ignoredAttributeNames);
            for (int i = 0; i < attributes.getLength(); i++) {
                if (!ignored.contains(attributes.getQName(i))) {
                    ret.put(attributes.getQName(i), substitute(attributes.getValue(i)));
                }
            }
            return ret;
        }

        private void fillExtraAttributes(DefaultExtendableItem item, Attributes attributes, String[] ignoredAttNames) {
            Map<String, String> extraAttributes = getExtraAttributes(attributes, ignoredAttNames);
            for (String name : extraAttributes.keySet()) {
                item.setExtraAttribute(name, extraAttributes.get(name));
            }
        }

        private PatternMatcher getMatcher(String matcherName) {
            return PatternMatchers.getInstance().getMatcher(matcherName);
        }

        // Handler to detect Gradle metadata redirects

        @Override
        public void startDTD(String name, String publicId, String systemId) {

        }

        @Override
        public void endDTD() {

        }

        @Override
        public void startEntity(String name) {

        }

        @Override
        public void endEntity(String name) {

        }

        @Override
        public void startCDATA() {

        }

        @Override
        public void endCDATA() {

        }

        @Override
        public void comment(char[] ch, int start, int length) {
            String comment = new String(ch, start, length);
            if (comment.contains(MetaDataParser.GRADLE_6_METADATA_MARKER) || comment.contains(MetaDataParser.GRADLE_METADATA_MARKER)) {
                hasGradleMetadataRedirect = true;
            }
        }
    }

    public static class ParserHelper {
        static final String JAXP_SCHEMA_LANGUAGE
                = "http://java.sun.com/xml/jaxp/properties/schemaLanguage";

        static final String JAXP_SCHEMA_SOURCE
                = "http://java.sun.com/xml/jaxp/properties/schemaSource";

        static final String XML_NAMESPACE_PREFIXES
                = "http://xml.org/sax/features/namespace-prefixes";

        static final String W3C_XML_SCHEMA = "http://www.w3.org/2001/XMLSchema";

        private static SAXParser newSAXParser(URL schema, InputStream schemaStream)
                throws ParserConfigurationException, SAXException {
            if (schema == null) {
                SAXParserFactory parserFactory = SAXParserFactory.newInstance();
                parserFactory.setValidating(false);
                parserFactory.setNamespaceAware(true);
                SAXParser parser = parserFactory.newSAXParser();
                parser.getXMLReader().setFeature(XML_NAMESPACE_PREFIXES, true);
                return parser;
            } else {
                SAXParserFactory parserFactory = SAXParserFactory.newInstance();
                parserFactory.setValidating(true);
                parserFactory.setNamespaceAware(true);

                SAXParser parser = parserFactory.newSAXParser();
                parser.setProperty(JAXP_SCHEMA_LANGUAGE, W3C_XML_SCHEMA);
                parser.setProperty(JAXP_SCHEMA_SOURCE, schemaStream);
                parser.getXMLReader().setFeature(XML_NAMESPACE_PREFIXES, true);
                return parser;
            }
        }

        public static void parse(
                URL xmlURL, URL schema, DefaultHandler handler)
                throws SAXException, IOException, ParserConfigurationException {
            try (InputStream xmlStream = URLHandlerRegistry.getDefault().openStream(xmlURL)) {
                InputSource inSrc = new InputSource(xmlStream);
                inSrc.setSystemId(xmlURL.toExternalForm());
                parse(inSrc, schema, handler);
            }
        }

        public static void parse(
                InputSource xmlStream, URL schema, DefaultHandler handler)
                throws SAXException, IOException, ParserConfigurationException {
            InputStream schemaStream = null;
            try {
                if (schema != null) {
                    schemaStream = URLHandlerRegistry.getDefault().openStream(schema);
                }

                // Set the context classloader to the bootstrap classloader, to work around how JAXP locates implementation classes
                // This should ensure that the JAXP classes provided by the JVM are used, rather than some other implementation
                ClassLoader original = Thread.currentThread().getContextClassLoader();
                Thread.currentThread().setContextClassLoader(ClassLoaderUtils.getPlatformClassLoader());
                try {
                    SAXParser parser = newSAXParser(schema, schemaStream);
                    parser.setProperty("http://xml.org/sax/properties/lexical-handler", handler);
                    parser.parse(xmlStream, handler);
                } finally {
                    Thread.currentThread().setContextClassLoader(original);
                }
            } finally {
                if (schemaStream != null) {
                    try {
                        schemaStream.close();
                    } catch (IOException ex) {
                        // ignored
                    }
                }
            }
        }
    }

    public String toString() {
        return "ivy parser";
    }
}
