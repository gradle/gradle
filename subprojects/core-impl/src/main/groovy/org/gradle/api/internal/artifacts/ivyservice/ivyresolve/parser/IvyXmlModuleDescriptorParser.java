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

import org.apache.ivy.core.IvyContext;
import org.apache.ivy.core.module.descriptor.*;
import org.apache.ivy.core.module.id.ArtifactId;
import org.apache.ivy.core.module.id.ModuleId;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.resolve.ResolveData;
import org.apache.ivy.core.resolve.ResolveEngine;
import org.apache.ivy.core.resolve.ResolveOptions;
import org.apache.ivy.core.resolve.ResolvedModuleRevision;
import org.apache.ivy.plugins.conflict.ConflictManager;
import org.apache.ivy.plugins.conflict.FixedConflictManager;
import org.apache.ivy.plugins.matcher.PatternMatcher;
import org.apache.ivy.plugins.namespace.NameSpaceHelper;
import org.apache.ivy.plugins.namespace.Namespace;
import org.apache.ivy.plugins.parser.AbstractModuleDescriptorParser;
import org.apache.ivy.plugins.parser.ModuleDescriptorParser;
import org.apache.ivy.plugins.parser.ParserSettings;
import org.apache.ivy.plugins.repository.Resource;
import org.apache.ivy.plugins.repository.url.URLResource;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.apache.ivy.util.Message;
import org.apache.ivy.util.XMLHelper;
import org.apache.ivy.util.extendable.ExtendableItemHelper;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Copied from org.apache.ivy.plugins.parser.xml.XmlModuleDescriptorParser into gradle codebase to make
 * it thread-safe.
 */
public class IvyXmlModuleDescriptorParser extends AbstractModuleDescriptorParser {
    static final String[] DEPENDENCY_REGULAR_ATTRIBUTES =
            new String[] {"org", "name", "branch", "branchConstraint", "rev", "revConstraint", "force", "transitive", "changing", "conf"};

    public static final String IVY_DATE_FORMAT_PATTERN = "yyyyMMddHHmmss";

    public DefaultModuleDescriptor parseDescriptor(ParserSettings ivySettings, URL xmlURL, Resource res,
            boolean validate) throws ParseException, IOException {
        Parser parser = new Parser(this, ivySettings, res);
        parser.setValidate(validate);
        parser.setInput(xmlURL);
        parser.parse();
        return (DefaultModuleDescriptor) parser.getModuleDescriptor();
    }

    public boolean accept(Resource res) {
        return true; // this the default parser, it thus accepts all resources
    }

    public void toIvyFile(InputStream is, Resource res, File destFile, ModuleDescriptor md)
            throws IOException, ParseException {
        throw new UnsupportedOperationException();
    }

    public static class Parser extends AbstractParser {
        public static final class State {
            public static final int NONE = 0;

            public static final int INFO = 1;

            public static final int CONF = 2;

            public static final int PUB = 3;

            public static final int DEP = 4;

            public static final int DEP_ARTIFACT = 5;

            public static final int ARTIFACT_INCLUDE = 6;

            public static final int ARTIFACT_EXCLUDE = 7;

            public static final int CONFLICT = 8;

            public static final int EXCLUDE = 9;

            public static final int DEPS = 10;

            public static final int DESCRIPTION = 11;

            public static final int EXTRA_INFO = 12;

            private State() {
            }
        }

        private static final List ALLOWED_VERSIONS = Arrays.asList("1.0", "1.1", "1.2", "1.3", "1.4", "2.0", "2.1", "2.2");

        /* how and what do we have to parse */
        private ParserSettings settings;
        private boolean validate = true;
        private URL descriptorURL;
        private InputStream descriptorInput;


        /* Parsing state */
        private int state = State.NONE;
        private PatternMatcher defaultMatcher;
        private DefaultDependencyDescriptor dd;
        private ConfigurationAware confAware;
        private MDArtifact artifact;
        private String conf;
        private boolean artifactsDeclared;
        private StringBuffer buffer;
        private String descriptorVersion;
        private String[] publicationsDefaultConf;

        public Parser(ModuleDescriptorParser moduleDescriptorParser, ParserSettings ivySettings, Resource res) {
            super(moduleDescriptorParser);
            settings = ivySettings;
            setResource(res);
        }
        
        public Parser newParser(Resource res) {
            Parser parser = new Parser(getModuleDescriptorParser(), settings, res);
            parser.setValidate(validate);
            return parser;
        }

        public void setInput(InputStream descriptorInput) {
            this.descriptorInput = descriptorInput;
        }

        public void setInput(URL descriptorURL) {
            this.descriptorURL = descriptorURL;
        }

        public void setValidate(boolean validate) {
            this.validate = validate;
        }

        public void parse() throws ParseException,
                IOException {
            try {
                URL schemaURL = validate ? getSchemaURL() : null;
                if (descriptorURL != null) {
                    XMLHelper.parse(descriptorURL, schemaURL, this);
                } else {
                    XMLHelper.parse(descriptorInput, schemaURL, this, null);
                }
                checkConfigurations();
                replaceConfigurationWildcards();
                getMd().setModuleArtifact(DefaultArtifact.newIvyArtifact(getMd().getResolvedModuleRevisionId(), getMd().getPublicationDate()));
                if (!artifactsDeclared) {
                    String[] configurationNames = getMd().getConfigurationsNames();
                    for (String configurationName : configurationNames) {
                        getMd().addArtifact(configurationName, new MDArtifact(getMd(), getMd().getModuleRevisionId().getName(), "jar", "jar"));
                    }
                }
                getMd().check();
            } catch (ParserConfigurationException ex) {
                IllegalStateException ise = new IllegalStateException(ex.getMessage() + " in "
                        + descriptorURL);
                ise.initCause(ex);
                throw ise;
            } catch (Exception ex) {
                checkErrors();
                ParseException pe = new ParseException(ex.getMessage() + " in " + descriptorURL, 0);
                pe.initCause(ex);
                throw pe;
            }
        }

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
                    if (!descriptorVersion.startsWith("1.")) {
                        Message.deprecated("using conflicts section is deprecated: "
                                + "please use hints section instead. Ivy file URL: " + descriptorURL);
                    }
                    state = State.CONFLICT;
                    checkConfigurations();
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
                    managerStarted(attributes, state == State.CONFLICT ? "name" : "manager");
                } else if ("override".equals(qName) && state == State.DEPS) {
                    mediationOverrideStarted(attributes);
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

        private String getDefaultParentLocation() {
            return "../ivy.xml";
        }

        private void extendsStarted(Attributes attributes) throws ParseException {
            String parentOrganisation = attributes.getValue("organisation");
            String parentModule = attributes.getValue("module");
            String parentRevision = attributes.getValue("revision");
            String location = elvis(attributes.getValue("location"), getDefaultParentLocation());

            String extendType = elvis(attributes.getValue("extendType"), "all").toLowerCase();
            List<String> extendTypes = Arrays.asList(extendType.split(","));

            ModuleDescriptor parent = null;
            try {
                Message.debug("Trying to parse included ivy file :" + location);
                parent = parseOtherIvyFileOnFileSystem(location);

                //verify that the parsed descriptor is the correct parent module.
                ModuleId expected = new ModuleId(parentOrganisation, parentModule);
                ModuleId pid = parent.getModuleRevisionId().getModuleId();
                if (!expected.equals(pid)) {
                    Message.verbose("Ignoring parent Ivy file " + location + "; expected " + expected + " but found " + pid);
                    parent = null;
                }

            } catch (ParseException e) {
                Message.warn("Unable to parse included ivy file " + location + ": " + e.getMessage());
            } catch (IOException e) {
                Message.warn("Unable to parse included ivy file " + location + ": " + e.getMessage());
            }

            // if the included ivy file is not found on file system, tries to resolve using
            // repositories
            if (parent == null) {
                try {
                    Message.debug(
                        "Trying to parse included ivy file by asking repository for module :"
                                    + parentOrganisation
                                    + "#"
                                    + parentModule
                                    + ";"
                                    + parentRevision);
                    parent = parseOtherIvyFile(parentOrganisation, parentModule, parentRevision);
                } catch (ParseException e) {
                    Message.warn("Unable to parse included ivy file for " + parentOrganisation + "#" + parentModule + ";" + parentRevision);
                }
            }

            if (parent == null) {
                throw new ParseException("Unable to parse included ivy file for " + parentOrganisation + "#" + parentModule + ";" + parentRevision, 0);
            }

            DefaultExtendsDescriptor ed = new DefaultExtendsDescriptor(
                    parent.getModuleRevisionId(),
                    parent.getResolvedModuleRevisionId(),
                    attributes.getValue("location"),
                    extendTypes.toArray(new String[extendTypes.size()]));
            getMd().addInheritedDescriptor(ed);

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

        private void mergeInfo(ModuleDescriptor parent) {
            ModuleRevisionId parentMrid = parent.getModuleRevisionId();

            DefaultModuleDescriptor descriptor = getMd();
            ModuleRevisionId currentMrid = descriptor.getModuleRevisionId();

            ModuleRevisionId mergedMrid = ModuleRevisionId.newInstance(
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

        private static Map mergeValues(Map inherited, Map overrides) {
            LinkedHashMap dup = new LinkedHashMap(inherited.size() + overrides.size());
            dup.putAll(inherited);
            dup.putAll(overrides);
            return dup;
        }

        private void mergeConfigurations(ModuleRevisionId sourceMrid, Configuration[] configurations) {
            DefaultModuleDescriptor md = getMd();
            for (Configuration configuration : configurations) {
                Message.debug("Merging configuration with: " + configuration.getName());
                //copy configuration from parent descriptor
                md.addConfiguration(new Configuration(configuration, sourceMrid));
            }
        }

        private void mergeDependencies(DependencyDescriptor[] dependencies) {
            DefaultModuleDescriptor md = getMd();
            for (DependencyDescriptor dependencyDescriptor : dependencies) {
                Message.debug("Merging dependency with: " + dependencyDescriptor.getDependencyRevisionId().toString());
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
            URL url = settings.getRelativeUrlResolver().getURL(descriptorURL, location);
            Message.debug("Trying to load included ivy file from " + url.toString());
            Parser parser = newParser(new URLResource(url));
            parser.parse();
            return parser.getModuleDescriptor();
        }

        private ModuleDescriptor parseOtherIvyFile(String parentOrganisation,
                String parentModule, String parentRevision) throws ParseException {
            ModuleId parentModuleId = new ModuleId(parentOrganisation, parentModule);
            ModuleRevisionId parentMrid = new ModuleRevisionId(parentModuleId, parentRevision);

            DependencyDescriptor dd = new DefaultDependencyDescriptor(parentMrid, true);
            ResolveData data = IvyContext.getContext().getResolveData();
            if (data == null) {
                ResolveEngine engine = IvyContext.getContext().getIvy().getResolveEngine();
                ResolveOptions options = new ResolveOptions();
                options.setDownload(false);
                data = new ResolveData(engine, options);
            }

            DependencyResolver resolver = settings.getResolver(parentMrid);
            if (resolver == null) {
                // TODO: Throw exception here?
                return null;
            } else {
                dd = NameSpaceHelper.toSystem(dd, settings.getContextNamespace());
                ResolvedModuleRevision otherModule = resolver.getDependency(dd, data);
                if (otherModule == null) {
                    throw new ParseException("Unable to find " + parentMrid.toString(), 0);
                }
                return otherModule.getDescriptor();
            }
        }

        private void publicationsStarted(Attributes attributes) {
            state = State.PUB;
            artifactsDeclared = true;
            checkConfigurations();
            String defaultConf = substitute(attributes.getValue("defaultconf"));
            if (defaultConf != null) {
                this.publicationsDefaultConf = defaultConf.split(",");
            }
        }

        private boolean isOtherNamespace(String qName) {
            return qName.indexOf(':') != -1;
        }

        private void managerStarted(Attributes attributes, String managerAtt) {
            String org = elvis(substitute(attributes.getValue("org")), PatternMatcher.ANY_EXPRESSION);
            String mod = elvis(substitute(attributes.getValue("module")), PatternMatcher.ANY_EXPRESSION);
            String name = substitute(attributes.getValue(managerAtt));
            String rev = substitute(attributes.getValue("rev"));
            ConflictManager cm;
            if (rev != null) {
                String[] revs = rev.split(",");
                for (int i = 0; i < revs.length; i++) {
                    revs[i] = revs[i].trim();
                }
                cm = new FixedConflictManager(revs);
            } else if (name != null) {
                cm = settings.getConflictManager(name);
                if (cm == null) {
                    addError("unknown conflict manager: " + name);
                    return;
                }
            } else {
                addError("bad conflict manager: no manager nor rev");
                return;
            }
            String matcherName = substitute(attributes.getValue("matcher"));
            PatternMatcher matcher = matcherName == null ? defaultMatcher : settings.getMatcher(matcherName);
            if (matcher == null) {
                addError("unknown matcher: " + matcherName);
                return;
            }
            getMd().addConflictManager(new ModuleId(org, mod), matcher, cm);
        }

        private void mediationOverrideStarted(Attributes attributes) {
            String org = elvis(substitute(attributes.getValue("org")), PatternMatcher.ANY_EXPRESSION);
            String mod = elvis(substitute(attributes.getValue("module")), PatternMatcher.ANY_EXPRESSION);
            String rev = substitute(attributes.getValue("rev"));
            String branch = substitute(attributes.getValue("branch"));
            String matcherName = substitute(attributes.getValue("matcher"));
            PatternMatcher matcher = matcherName == null ? defaultMatcher : settings.getMatcher(matcherName);
            if (matcher == null) {
                addError("unknown matcher: " + matcherName);
                return;
            }
            getMd().addDependencyDescriptorMediator(new ModuleId(org, mod), matcher, new OverrideDependencyDescriptorMediator(branch, rev));
        }

        private void includeConfStarted(Attributes attributes)
                throws SAXException, IOException, ParserConfigurationException, ParseException {
            URL url = settings.getRelativeUrlResolver().getURL(descriptorURL, substitute(attributes.getValue("file")), substitute(attributes.getValue("url")));
            if (url == null) {
                throw new SAXException("include tag must have a file or an url attribute");
            }

            // create a new temporary parser to read the configurations from
            // the specified file.
            Parser parser = newParser(new URLResource(url));
            parser.setInput(url);
            XMLHelper.parse(url , null, parser);

            // add the configurations from this temporary parser to this module descriptor
            Configuration[] configs = parser.getModuleDescriptor().getConfigurations();
            for (Configuration config : configs) {
                getMd().addConfiguration(config);
            }
            if (parser.getDefaultConfMapping() != null) {
                Message.debug("setting default conf mapping from imported configurations file: " + parser.getDefaultConfMapping());
                setDefaultConfMapping(parser.getDefaultConfMapping());
            }
            if (parser.getDefaultConf() != null) {
                Message.debug("setting default conf from imported configurations file: " + parser.getDefaultConf());
                setDefaultConf(parser.getDefaultConf());
            }
            if (parser.getMd().isMappingOverride()) {
                Message.debug("enabling mapping-override from imported configurations file");
                getMd().setMappingOverride(true);
            }
        }

        private void confStarted(Attributes attributes) {
            String conf = substitute(attributes.getValue("name"));
            switch (state) {
                case State.CONF:
                    Configuration.Visibility visibility = Configuration.Visibility.getVisibility(elvis(substitute(attributes.getValue("visibility")), "public"));
                    String description = substitute(attributes.getValue("description"));
                    String[] extend = substitute(attributes.getValue("extends")) == null ? null : substitute(attributes.getValue("extends")).split(",");
                    String transitiveValue = attributes.getValue("transitive");
                    boolean transitive = (transitiveValue == null) || Boolean.valueOf(attributes.getValue("transitive"));
                    String deprecated = attributes.getValue("deprecated");
                    Configuration configuration = new Configuration(conf, visibility, description, extend, transitive, deprecated);
                    ExtendableItemHelper.fillExtraAttributes(settings, configuration, attributes, 
                            new String[]{"name", "visibility", "extends", "transitive", "description", "deprecated"});
                    getMd().addConfiguration(configuration);
                    break;
                case State.PUB:
                    if ("*".equals(conf)) {
                        String[] confs = getMd().getConfigurationsNames();
                        for (String confName : confs) {
                            artifact.addConfiguration(confName);
                            getMd().addArtifact(confName, artifact);
                        }
                    } else {
                        artifact.addConfiguration(conf);
                        getMd().addArtifact(conf, artifact);
                    }
                    break;
                case State.DEP:
                    this.conf = conf;
                    String mappeds = substitute(attributes.getValue("mapped"));
                    if (mappeds != null) {
                        String[] mapped = mappeds.split(",");
                        for (String depConf : mapped) {
                            dd.addDependencyConfiguration(conf, depConf.trim());
                        }
                    }
                    break;
                case State.DEP_ARTIFACT:
                case State.ARTIFACT_INCLUDE:
                case State.ARTIFACT_EXCLUDE:
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
            boolean force = Boolean.valueOf(substitute(attributes.getValue("force")));
            boolean changing = Boolean.valueOf(substitute(attributes.getValue("changing")));

            String transitiveValue = substitute(attributes.getValue("transitive"));
            boolean transitive = (transitiveValue == null) ? true : Boolean.valueOf(transitiveValue);

            String name = substitute(attributes.getValue("name"));
            String branch = substitute(attributes.getValue("branch"));
            String branchConstraint = substitute(attributes.getValue("branchConstraint"));
            String rev = substitute(attributes.getValue("rev"));
            String revConstraint = substitute(attributes.getValue("revConstraint"));

            Map extraAttributes = ExtendableItemHelper.getExtraAttributes(settings, attributes, DEPENDENCY_REGULAR_ATTRIBUTES);

            ModuleRevisionId revId = ModuleRevisionId.newInstance(org, name, branch, rev, extraAttributes);
            ModuleRevisionId dynamicId;
            if ((revConstraint == null) && (branchConstraint == null)) {
                // no dynamic constraints defined, so dynamicId equals revId
                dynamicId = ModuleRevisionId.newInstance(org, name, branch, rev, extraAttributes, false);
            } else {
                if (branchConstraint == null) {
                    // this situation occurs when there was no branch defined
                    // in the original dependency descriptor. So the dynamicId
                    // shouldn't contain a branch neither
                    dynamicId = ModuleRevisionId.newInstance(org, name, null, revConstraint, extraAttributes, false);
                } else {
                    dynamicId = ModuleRevisionId.newInstance(org, name, branchConstraint, revConstraint, extraAttributes);
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
                String url = substitute(attributes.getValue("url"));
                Map extraAttributes = ExtendableItemHelper.getExtraAttributes(settings, attributes, new String[]{"ext", "type", "name", "conf"});
                artifact = new MDArtifact(getMd(), artName, type, ext, url == null ? null : new URL(url), extraAttributes);
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
                        getMd().addArtifact(confName.trim(), artifact);
                    }
                }
            } else if (state == State.DEP) {
                // this is an artifact asked for a particular dependency
                addDependencyArtifacts(qName, attributes);
            } else if (validate) {
                addError("artifact tag found in invalid tag: " + state);
            }
        }

        private void dependenciesStarted(Attributes attributes) {
            state = State.DEPS;
            String defaultConf = substitute(attributes.getValue("defaultconf"));
            if (defaultConf != null) {
                setDefaultConf(defaultConf);
            }
            String defaultConfMapping = substitute(attributes.getValue("defaultconfmapping"));
            if (defaultConf != null) {
                setDefaultConfMapping(defaultConfMapping);
            }
            String confMappingOverride = substitute(attributes.getValue("confmappingoverride"));
            if (confMappingOverride != null) {
                getMd().setMappingOverride(Boolean.valueOf(confMappingOverride));
            }
            checkConfigurations();
        }

        private void configurationStarted(Attributes attributes) {
            state = State.CONF;
            setDefaultConfMapping(substitute(attributes.getValue("defaultconfmapping")));
            setDefaultConf(substitute(attributes.getValue("defaultconf")));
            getMd().setMappingOverride(Boolean.valueOf(substitute(attributes.getValue("confmappingoverride"))));
        }

        private void infoStarted(Attributes attributes) {
            state = State.INFO;
            String org = substitute(attributes.getValue("organisation"));
            String module = substitute(attributes.getValue("module"));
            String revision = substitute(attributes.getValue("revision"));
            String branch = substitute(attributes.getValue("branch"));
            Map extraAttributes = ExtendableItemHelper.getExtraAttributes(settings, attributes, 
                    new String[]{"organisation", "module", "revision", "status", "publication", "branch", "namespace", "default", "resolver"});
            getMd().setModuleRevisionId(ModuleRevisionId.newInstance(org, module, branch, revision, extraAttributes));

            String namespace = substitute(attributes.getValue("namespace"));
            if (namespace != null) {
                Namespace ns = settings.getNamespace(namespace);
                if (ns == null) {
                    Message.warn("namespace not found for " + getMd().getModuleRevisionId() + ": " + namespace);
                } else {
                    getMd().setNamespace(ns);
                }
            }

            getMd().setStatus(elvis(substitute(attributes.getValue("status")), settings.getStatusManager().getDefaultStatus()));
            getMd().setDefault(Boolean.valueOf(substitute(attributes.getValue("default"))));
            String pubDate = substitute(attributes.getValue("publication"));
            if (pubDate != null && pubDate.length() > 0) {
                try {
                    final SimpleDateFormat ivyDateFormat = new SimpleDateFormat(IVY_DATE_FORMAT_PATTERN);
                    getMd().setPublicationDate(ivyDateFormat.parse(pubDate));
                } catch (ParseException e) {
                    addError("invalid publication date format: " + pubDate);
                    getMd().setPublicationDate(getDefaultPubDate());
                }
            } else {
                getMd().setPublicationDate(getDefaultPubDate());
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
                Message.debug("post 1.3 ivy file: using " + PatternMatcher.EXACT + " as default matcher");
                defaultMatcher = settings.getMatcher(PatternMatcher.EXACT);
            } else {
                Message.debug("pre 1.3 ivy file: using " + PatternMatcher.EXACT_OR_REGEXP + " as default matcher");
                defaultMatcher = settings.getMatcher(PatternMatcher.EXACT_OR_REGEXP);
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
                            : PatternMatcher.ANY_EXPRESSION;
                }
            }
            String type = substitute(attributes.getValue("type"));
            if (type == null) {
                type = "artifact".equals(tag) ? "jar" : PatternMatcher.ANY_EXPRESSION;
            }
            String ext = substitute(attributes.getValue("ext"));
            ext = ext != null ? ext : type;
            if (state == State.DEP_ARTIFACT) {
                String url = substitute(attributes.getValue("url"));
                Map extraAttributes = ExtendableItemHelper.getExtraAttributes(settings, attributes, new String[]{"name", "type", "ext", "url", "conf"});
                confAware = new DefaultDependencyArtifactDescriptor(dd, name, type, ext, url == null ? null : new URL(url), extraAttributes);
            } else if (state == State.ARTIFACT_INCLUDE) {
                PatternMatcher matcher = getPatternMatcher(attributes.getValue("matcher"));
                String org = elvis(substitute(attributes.getValue("org")), PatternMatcher.ANY_EXPRESSION);
                String module = elvis(substitute(attributes.getValue("module")), PatternMatcher.ANY_EXPRESSION);
                ArtifactId aid = new ArtifactId(new ModuleId(org, module), name, type, ext);
                Map extraAttributes = ExtendableItemHelper.getExtraAttributes(settings, attributes, new String[]{"org", "module", "name", "type", "ext", "matcher", "conf"});
                confAware = new DefaultIncludeRule(aid, matcher, extraAttributes);
            } else { // _state == ARTIFACT_EXCLUDE || EXCLUDE
                PatternMatcher matcher = getPatternMatcher(attributes.getValue("matcher"));
                String org = elvis(substitute(attributes.getValue("org")), PatternMatcher.ANY_EXPRESSION);
                String module = elvis(substitute(attributes.getValue("module")), PatternMatcher.ANY_EXPRESSION);
                ArtifactId aid = new ArtifactId(new ModuleId(org, module), name, type, ext);
                Map extraAttributes = ExtendableItemHelper.getExtraAttributes(settings, attributes, new String[]{"org", "module", "name", "type", "ext", "matcher", "conf"});
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
            PatternMatcher matcher = matcherName == null ? defaultMatcher : settings
                    .getMatcher(matcherName);
            if (matcher == null) {
                throw new IllegalArgumentException("unknown matcher " + matcherName);
            }
            return matcher;
        }


        public void characters(char[] ch, int start, int length) throws SAXException {
            if (buffer != null) {
                buffer.append(ch, start, length);
            }
        }


        public void endElement(String uri, String localName, String qName) throws SAXException {
            if (state == State.PUB && "artifact".equals(qName) && artifact.getConfigurations().length == 0) {
                String[] confs = publicationsDefaultConf == null ? getMd().getConfigurationsNames() : publicationsDefaultConf;
                for (String confName : confs) {
                    artifact.addConfiguration(confName.trim());
                    getMd().addArtifact(confName.trim(), artifact);
                }
            } else if ("configurations".equals(qName)) {
                checkConfigurations();
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
                state = State.NONE;
            } else if (state == State.DESCRIPTION && "description".equals(qName)) {
                getMd().setDescription(buffer == null ? "" : buffer.toString().trim());
                buffer = null;
                state = State.INFO;
            } else if (state == State.EXTRA_INFO) {
                getMd().addExtraInfo(qName, buffer == null ? "" : buffer.toString());
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

        private void checkConfigurations() {
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
            return getClass().getResource("ivy.xsd");
        }

        private String elvis(String value, String defaultValue) {
            return value != null ? value : defaultValue;
        }

        private String substitute(String name) {
            return settings.substitute(name);
        }

    }

    public String toString() {
        return "ivy parser";
    }
}