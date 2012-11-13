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

import org.apache.ivy.Ivy;
import org.apache.ivy.core.IvyContext;
import org.apache.ivy.core.cache.ResolutionCacheManager;
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
import org.apache.ivy.plugins.parser.ModuleDescriptorParserRegistry;
import org.apache.ivy.plugins.parser.ParserSettings;
import org.apache.ivy.plugins.parser.xml.UpdateOptions;
import org.apache.ivy.plugins.parser.xml.XmlModuleDescriptorUpdater;
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
    static final String[] DEPENDENCY_REGULAR_ATTRIBUTES = new String[] {"org", "name", "branch",
            "branchConstraint", "rev", "revConstraint", "force", "transitive", "changing", "conf"};

    public static final String IVY_DATE_FORMAT_PATTERN = "yyyyMMddHHmmss";

    /**
     * @param settings
     * @param xmlURL
     *            the url pointing to the file to parse
     * @param res
     *            the real resource to parse, used for log only
     * @param validate
     * @return
     * @throws java.text.ParseException
     * @throws java.io.IOException
     */
    public ModuleDescriptor parseDescriptor(ParserSettings ivySettings, URL xmlURL, Resource res,
            boolean validate) throws ParseException, IOException {
        Parser parser = newParser(ivySettings);
        parser.setValidate(validate);
        parser.setResource(res);
        parser.setInput(xmlURL);
        parser.parse();
        return parser.getModuleDescriptor();
    }

    /** Used for test purpose */
    ModuleDescriptor parseDescriptor(ParserSettings ivySettings, InputStream descriptor,
            Resource res, boolean validate) throws ParseException, IOException {
        Parser parser = newParser(ivySettings);
        parser.setValidate(validate);
        parser.setResource(res);
        parser.setInput(descriptor);
        parser.parse();
        return parser.getModuleDescriptor();
    }

    /**
     * Instantiates a Parser instance responsible for actual parsing of Ivy files.
     * <p>
     * Override this method if you want to use a custom Parser.
     * </p>
     *
     * @param ivySettings
     *            the settings to use during parsing
     * @return the Parser instance used for parsing Ivy files
     */
    protected Parser newParser(ParserSettings ivySettings) {
        return new Parser(this, ivySettings);
    }

    public boolean accept(Resource res) {
        return true; // this the default parser, it thus accepts all resources
    }

    public void toIvyFile(InputStream is, Resource res, File destFile, ModuleDescriptor md)
            throws IOException, ParseException {
        try {
            Namespace ns = null;
            if (md instanceof DefaultModuleDescriptor) {
                DefaultModuleDescriptor dmd = (DefaultModuleDescriptor) md;
                ns = dmd.getNamespace();
            }
            XmlModuleDescriptorUpdater.update(is, res, destFile,
                    new UpdateOptions()
                            .setSettings(IvyContext.getContext().getSettings())
                            .setStatus(md.getStatus())
                            .setRevision(md.getResolvedModuleRevisionId().getRevision())
                            .setPubdate(md.getResolvedPublicationDate())
                            .setUpdateBranch(false)
                            .setNamespace(ns));
        } catch (SAXException e) {
            ParseException ex = new ParseException("exception occurred while parsing " + res, 0);
            ex.initCause(e);
            throw ex;
        } finally {
            if (is != null) {
                is.close();
            }
        }
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

        protected static final List ALLOWED_VERSIONS = Arrays.asList(
                new String[]{"1.0", "1.1", "1.2", "1.3", "1.4", "2.0", "2.1", "2.2"});

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

        public Parser(ModuleDescriptorParser parser, ParserSettings ivySettings) {
            super(parser);
            settings = ivySettings;
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
                getMd().setModuleArtifact(
                    DefaultArtifact.newIvyArtifact(
                        getMd().getResolvedModuleRevisionId(), getMd().getPublicationDate()));
                if (!artifactsDeclared) {
                    String[] confs = getMd().getConfigurationsNames();
                    for (int i = 0; i < confs.length; i++) {
                        getMd().addArtifact(confs[i],
                            new MDArtifact(getMd(), getMd().getModuleRevisionId().getName(),
                                "jar", "jar"));
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
                    getBuffer().append("<").append(qName);
                    for (int i = 0; i < attributes.getLength(); i++) {
                        getBuffer().append(" ");
                        getBuffer().append(attributes.getQName(i));
                        getBuffer().append("=\"");
                        getBuffer().append(attributes.getValue(i));
                        getBuffer().append("\"");
                    }
                    getBuffer().append(">");
                    return;
                } else if ("ivy-module".equals(qName)) {
                    ivyModuleStarted(attributes);
                } else if ("info".equals(qName)) {
                    infoStarted(attributes);
                } else if (state == State.INFO && "extends".equals(qName)) {
                    extendsStarted(attributes);
                } else if (state == State.INFO && "license".equals(qName)) {
                    getMd().addLicense(new License(settings.substitute(attributes.getValue("name")),
                                        settings.substitute(attributes.getValue("url"))));
                } else if (state == State.INFO && "description".equals(qName)) {
                    getMd().setHomePage(settings.substitute(attributes.getValue("homepage")));
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
                    dd.addDependencyConfiguration(conf, settings.substitute(attributes
                            .getValue("name")));
                } else if (("conflict".equals(qName) && state == State.DEPS)
                        || "manager".equals(qName) && state == State.CONFLICT) {
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

        protected String getDefaultParentLocation() {
            return "../ivy.xml";
        }

        protected void extendsStarted(Attributes attributes) throws ParseException {
            String parentOrganisation = attributes.getValue("organisation");
            String parentModule = attributes.getValue("module");
            String parentRevision = attributes.getValue("revision");
            String location = attributes.getValue("location") != null ? attributes
                    .getValue("location") : getDefaultParentLocation();
            ModuleDescriptor parent = null;

            String extendType = attributes.getValue("extendType") != null ? attributes.getValue(
                "extendType").toLowerCase() : "all";

            List/* <String> */extendTypes = Arrays.asList(extendType.split(","));

            try {
                Message.debug("Trying to parse included ivy file :" + location);
                parent = parseOtherIvyFileOnFileSystem(location);

                //verify that the parsed descriptor is the correct parent module.
                ModuleId expected = new ModuleId(parentOrganisation, parentModule);
                ModuleId pid = parent.getModuleRevisionId().getModuleId();
                if (!expected.equals(pid)) {
                    Message.verbose("Ignoring parent Ivy file " + location + "; expected "
                        + expected + " but found " + pid);
                    parent = null;
                }

            } catch (ParseException e) {
                Message.warn("Unable to parse included ivy file " + location + ": "
                    + e.getMessage());
            } catch (IOException e) {
                Message.warn("Unable to parse included ivy file " + location + ": "
                    + e.getMessage());
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
                    Message.warn("Unable to parse included ivy file for " + parentOrganisation
                            + "#" + parentModule + ";" + parentRevision);
                }
            }

            if (parent == null) {
                throw new ParseException("Unable to parse included ivy file for "
                        + parentOrganisation + "#" + parentModule + ";" + parentRevision, 0);
            }

            ResolutionCacheManager cacheManager = settings.getResolutionCacheManager();

            File ivyFileInCache = cacheManager.getResolvedIvyFileInCache(parent
                    .getResolvedModuleRevisionId());
            //Generate the parent cache file if necessary
            if (parent.getResource() != null
                    && !parent.getResource().getName().equals(ivyFileInCache.toURI().toString())) {
                try {
                    parent.toIvyFile(ivyFileInCache);
                } catch (ParseException e) {
                    throw new ParseException("Unable to create cache file for "
                            + parentOrganisation + "#" + parentModule + ";" + parentRevision
                            + " Reason:" + e.getLocalizedMessage(), 0);
                } catch (IOException e) {
                    throw new ParseException("Unable to create cache file for "
                            + parentOrganisation + "#" + parentModule + ";" + parentRevision
                            + " Reason :" + e.getLocalizedMessage(), 0);
                }
            }

            DefaultExtendsDescriptor ed = new DefaultExtendsDescriptor(
                    parent.getModuleRevisionId(),
                    parent.getResolvedModuleRevisionId(),
                    attributes.getValue("location"),
                    (String[]) extendTypes.toArray(new String[extendTypes.size()]));
            getMd().addInheritedDescriptor(ed);

            mergeWithOtherModuleDescriptor(extendTypes, parent);
        }

        protected void mergeWithOtherModuleDescriptor(List/* <String> */extendTypes,
                ModuleDescriptor parent) {

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

        protected void mergeAll(ModuleDescriptor parent) {
            ModuleRevisionId sourceMrid = parent.getModuleRevisionId();
            mergeInfo(parent);
            mergeConfigurations(sourceMrid, parent.getConfigurations());
            mergeDependencies(parent.getDependencies());
            mergeDescription(parent.getDescription());
        }

        protected void mergeInfo(ModuleDescriptor parent) {
            ModuleRevisionId parentMrid = parent.getModuleRevisionId();

            DefaultModuleDescriptor descriptor = getMd();
            ModuleRevisionId currentMrid = descriptor.getModuleRevisionId();

            ModuleRevisionId mergedMrid = ModuleRevisionId.newInstance(
                mergeValue(parentMrid.getOrganisation(), currentMrid.getOrganisation()),
                currentMrid.getName(),
                mergeValue(parentMrid.getBranch(), currentMrid.getBranch()),
                mergeValue(parentMrid.getRevision(), currentMrid.getRevision()),
                mergeValues(parentMrid.getQualifiedExtraAttributes(),
                            currentMrid.getQualifiedExtraAttributes())
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

        protected void mergeConfigurations(ModuleRevisionId sourceMrid, Configuration[] configurations) {
            DefaultModuleDescriptor md = getMd();
            for (int i = 0; i < configurations.length; i++) {
                Configuration configuration = configurations[i];
                Message.debug("Merging configuration with: " + configuration.getName());
                //copy configuration from parent descriptor
                md.addConfiguration(new Configuration(configuration, sourceMrid));
            }
        }

        protected void mergeDependencies(DependencyDescriptor[] dependencies) {
            DefaultModuleDescriptor md = getMd();
            for (int i = 0; i < dependencies.length; i++) {
                DependencyDescriptor dependencyDescriptor = dependencies[i];
                Message.debug("Merging dependency with: "
                        + dependencyDescriptor.getDependencyRevisionId().toString());
                md.addDependency(dependencyDescriptor);
            }
        }

        protected void mergeDescription(String description) {
            String current = getMd().getDescription();
            if (current == null || current.trim().length() == 0) {
                getMd().setDescription(description);
            }
        }

        protected ModuleDescriptor parseOtherIvyFileOnFileSystem(String location)
                throws ParseException, IOException {
            URL url = null;
            ModuleDescriptor parent = null;
            url = getSettings().getRelativeUrlResolver().getURL(descriptorURL, location);
            Message.debug("Trying to load included ivy file from " + url.toString());
            URLResource res = new URLResource(url);
            ModuleDescriptorParser parser = ModuleDescriptorParserRegistry.getInstance().getParser(
                res);

            parent = parser.parseDescriptor(getSettings(), url, isValidate());
            return parent;
        }

        protected ModuleDescriptor parseOtherIvyFile(String parentOrganisation,
                String parentModule, String parentRevision) throws ParseException {
            ModuleId parentModuleId = new ModuleId(parentOrganisation, parentModule);
            ModuleRevisionId parentMrid = new ModuleRevisionId(parentModuleId, parentRevision);

            // try to load parent module in cache
            File cacheFile = settings.getResolutionCacheManager().getResolvedIvyFileInCache(
                ModuleRevisionId.newInstance(parentMrid, Ivy.getWorkingRevision()));
            if (cacheFile.exists() && cacheFile.length() > 0) {
                ModuleDescriptor md;
                try {
                    Message.debug("Trying to load included ivy file from cache");
                    URL parentUrl = cacheFile.toURI().toURL();
                    md = parseOtherIvyFileOnFileSystem(parentUrl.toString());
                    return md;
                } catch (IOException e) {
                    // do nothing
                    Message.error(e.getLocalizedMessage());
                }
            }

            DependencyDescriptor dd = new DefaultDependencyDescriptor(parentMrid, true);
            ResolveData data = IvyContext.getContext().getResolveData();
            if (data == null) {
                ResolveEngine engine = IvyContext.getContext().getIvy().getResolveEngine();
                ResolveOptions options = new ResolveOptions();
                options.setDownload(false);
                data = new ResolveData(engine, options);
            }

            DependencyResolver resolver = getSettings().getResolver(parentMrid);
            if (resolver == null) {
                // TODO: Throw exception here?
                return null;
            } else {
                dd = NameSpaceHelper.toSystem(dd, getSettings().getContextNamespace());
                ResolvedModuleRevision otherModule = resolver.getDependency(dd, data);
                if (otherModule == null) {
                    throw new ParseException("Unable to find " + parentMrid.toString(), 0);
                }
                return otherModule.getDescriptor();
            }

        }

        protected void publicationsStarted(Attributes attributes) {
            state = State.PUB;
            artifactsDeclared = true;
            checkConfigurations();
            String defaultConf = settings.substitute(attributes.getValue("defaultconf"));
            if (defaultConf != null) {
                setPublicationsDefaultConf(defaultConf);
            }
        }

        protected void setPublicationsDefaultConf(String defaultConf) {
            this.publicationsDefaultConf = defaultConf == null ? null : defaultConf.split(",");
        }

        protected boolean isOtherNamespace(String qName) {
            return qName.indexOf(':') != -1;
        }

        protected void managerStarted(Attributes attributes, String managerAtt) {
            String org = settings.substitute(attributes.getValue("org"));
            org = org == null ? PatternMatcher.ANY_EXPRESSION : org;
            String mod = settings.substitute(attributes.getValue("module"));
            mod = mod == null ? PatternMatcher.ANY_EXPRESSION : mod;
            ConflictManager cm;
            String name = settings.substitute(attributes.getValue(managerAtt));
            String rev = settings.substitute(attributes.getValue("rev"));
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
            String matcherName = settings.substitute(attributes.getValue("matcher"));
            PatternMatcher matcher = matcherName == null ? defaultMatcher : settings
                    .getMatcher(matcherName);
            if (matcher == null) {
                addError("unknown matcher: " + matcherName);
                return;
            }
            getMd().addConflictManager(new ModuleId(org, mod), matcher, cm);
        }

        protected void mediationOverrideStarted(Attributes attributes) {
            String org = settings.substitute(attributes.getValue("org"));
            org = org == null ? PatternMatcher.ANY_EXPRESSION : org;
            String mod = settings.substitute(attributes.getValue("module"));
            mod = mod == null ? PatternMatcher.ANY_EXPRESSION : mod;
            String rev = settings.substitute(attributes.getValue("rev"));
            String branch = settings.substitute(attributes.getValue("branch"));
            String matcherName = settings.substitute(attributes.getValue("matcher"));
            PatternMatcher matcher = matcherName == null ? defaultMatcher : settings
                    .getMatcher(matcherName);
            if (matcher == null) {
                addError("unknown matcher: " + matcherName);
                return;
            }
            getMd().addDependencyDescriptorMediator(
                new ModuleId(org, mod), matcher,
                new OverrideDependencyDescriptorMediator(branch, rev));
        }

        protected void includeConfStarted(Attributes attributes)
                throws SAXException, IOException, ParserConfigurationException, ParseException {
            URL url = settings.getRelativeUrlResolver().getURL(descriptorURL,
                    settings.substitute(attributes.getValue("file")),
                    settings.substitute(attributes.getValue("url")));

            if (url == null) {
                throw new SAXException("include tag must have a file or an url attribute");
            }

            // create a new temporary parser to read the configurations from
            // the specified file.
            Parser parser = new Parser(getModuleDescriptorParser(), settings);
            parser.setInput(url);
            parser.setMd(new DefaultModuleDescriptor(getModuleDescriptorParser(),
                    new URLResource(url)));
            XMLHelper.parse(url , null, parser);

            // add the configurations from this temporary parser to this module descriptor
            Configuration[] configs = parser.getModuleDescriptor().getConfigurations();
            for (int i = 0; i < configs.length; i++) {
                getMd().addConfiguration(configs[i]);
            }
            if (parser.getDefaultConfMapping() != null) {
                Message.debug("setting default conf mapping from imported configurations file: "
                        + parser.getDefaultConfMapping());
                setDefaultConfMapping(parser.getDefaultConfMapping());
            }
            if (parser.getDefaultConf() != null) {
                Message.debug("setting default conf from imported configurations file: "
                        + parser.getDefaultConf());
                setDefaultConf(parser.getDefaultConf());
            }
            if (parser.getMd().isMappingOverride()) {
                Message.debug("enabling mapping-override from imported configurations"
                        + " file");
                getMd().setMappingOverride(true);
            }
        }

        protected void confStarted(Attributes attributes) {
            String conf = settings.substitute(attributes.getValue("name"));
            switch (state) {
                case State.CONF:
                    String visibility = settings.substitute(attributes.getValue("visibility"));
                    String ext = settings.substitute(attributes.getValue("extends"));
                    String transitiveValue = attributes.getValue("transitive");
                    boolean transitive = (transitiveValue == null) ? true : Boolean
                            .valueOf(attributes.getValue("transitive")).booleanValue();
                    String deprecated = attributes.getValue("deprecated");
                    Configuration configuration = new Configuration(conf,
                            Configuration.Visibility
                                    .getVisibility(visibility == null ? "public"
                                            : visibility), settings.substitute(attributes
                                    .getValue("description")), ext == null ? null : ext
                                    .split(","), transitive, deprecated);
                    ExtendableItemHelper.fillExtraAttributes(settings, configuration, attributes,
                            new String[]{"name", "visibility", "extends", "transitive",
                                    "description", "deprecated"});
                    getMd().addConfiguration(configuration);
                    break;
                case State.PUB:
                    if ("*".equals(conf)) {
                        String[] confs = getMd().getConfigurationsNames();
                        for (int i = 0; i < confs.length; i++) {
                            artifact.addConfiguration(confs[i]);
                            getMd().addArtifact(confs[i], artifact);
                        }
                    } else {
                        artifact.addConfiguration(conf);
                        getMd().addArtifact(conf, artifact);
                    }
                    break;
                case State.DEP:
                    this.conf = conf;
                    String mappeds = settings.substitute(attributes.getValue("mapped"));
                    if (mappeds != null) {
                        String[] mapped = mappeds.split(",");
                        for (int i = 0; i < mapped.length; i++) {
                            dd.addDependencyConfiguration(conf, mapped[i].trim());
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

        protected void dependencyStarted(Attributes attributes) {
            state = State.DEP;
            String org = settings.substitute(attributes.getValue("org"));
            if (org == null) {
                org = getMd().getModuleRevisionId().getOrganisation();
            }
            boolean force = Boolean.valueOf(settings.substitute(attributes.getValue("force")))
                    .booleanValue();
            boolean changing = Boolean.valueOf(
                settings.substitute(attributes.getValue("changing"))).booleanValue();

            String transitiveValue = settings.substitute(attributes.getValue("transitive"));
            boolean transitive = (transitiveValue == null) ? true : Boolean.valueOf(
                attributes.getValue("transitive")).booleanValue();

            String name = settings.substitute(attributes.getValue("name"));
            String branch = settings.substitute(attributes.getValue("branch"));
            String branchConstraint = settings.substitute(attributes.getValue("branchConstraint"));

//            if (branchConstraint == null) {
//                // there was no branch constraint before, so we should
//                // set the branchConstraint to the current default branch
//                branchConstraint = settings.getDefaultBranch(ModuleId.newInstance(org, name));
//            }

            String rev = settings.substitute(attributes.getValue("rev"));
            String revConstraint = settings.substitute(attributes.getValue("revConstraint"));

            Map extraAttributes = ExtendableItemHelper.getExtraAttributes(
                settings, attributes, DEPENDENCY_REGULAR_ATTRIBUTES);

            ModuleRevisionId revId = ModuleRevisionId.newInstance(org, name, branch, rev,
                extraAttributes);
            ModuleRevisionId dynamicId = null;
            if ((revConstraint == null) && (branchConstraint == null)) {
                // no dynamic constraints defined, so dynamicId equals revId
                dynamicId = ModuleRevisionId.newInstance(org, name, branch, rev,
                                extraAttributes, false);
            } else {
                if (branchConstraint == null) {
                    // this situation occurs when there was no branch defined
                    // in the original dependency descriptor. So the dynamicId
                    // shouldn't contain a branch neither
                    dynamicId = ModuleRevisionId.newInstance(org, name, null, revConstraint,
                                    extraAttributes, false);
                } else {
                    dynamicId = ModuleRevisionId.newInstance(org, name, branchConstraint,
                                    revConstraint, extraAttributes);
                }
            }

            dd = new DefaultDependencyDescriptor(getMd(), revId, dynamicId, force, changing, transitive);
            getMd().addDependency(dd);
            String confs = settings.substitute(attributes.getValue("conf"));
            if (confs != null && confs.length() > 0) {
                parseDepsConfs(confs, dd);
            }
        }

        protected void artifactStarted(String qName, Attributes attributes)
                throws MalformedURLException {
            if (state == State.PUB) {
                // this is a published artifact
                String artName = settings.substitute(attributes.getValue("name"));
                artName = artName == null ? getMd().getModuleRevisionId().getName() : artName;
                String type = settings.substitute(attributes.getValue("type"));
                type = type == null ? "jar" : type;
                String ext = settings.substitute(attributes.getValue("ext"));
                ext = ext != null ? ext : type;
                String url = settings.substitute(attributes.getValue("url"));
                artifact = new MDArtifact(getMd(), artName, type, ext, url == null ? null
                        : new URL(url), ExtendableItemHelper.getExtraAttributes(
                            settings, attributes, new String[] {"ext", "type", "name", "conf"}));
                String confs = settings.substitute(attributes.getValue("conf"));
                // only add confs if they are specified. if they aren't, endElement will
                // handle this
                // only if there are no conf defined in sub elements
                if (confs != null && confs.length() > 0) {
                    String[] conf;
                    if ("*".equals(confs)) {
                        conf = getMd().getConfigurationsNames();
                    } else {
                        conf = confs.split(",");
                    }
                    for (int i = 0; i < conf.length; i++) {
                        artifact.addConfiguration(conf[i].trim());
                        getMd().addArtifact(conf[i].trim(), artifact);
                    }
                }
            } else if (state == State.DEP) {
                // this is an artifact asked for a particular dependency
                addDependencyArtifacts(qName, attributes);
            } else if (validate) {
                addError("artifact tag found in invalid tag: " + state);
            }
        }

        protected void dependenciesStarted(Attributes attributes) {
            state = State.DEPS;
            String defaultConf = settings.substitute(attributes.getValue("defaultconf"));
            if (defaultConf != null) {
                setDefaultConf(defaultConf);
            }
            defaultConf = settings.substitute(attributes.getValue("defaultconfmapping"));
            if (defaultConf != null) {
                setDefaultConfMapping(defaultConf);
            }
            String confMappingOverride = settings.substitute(attributes
                    .getValue("confmappingoverride"));
            if (confMappingOverride != null) {
                getMd().setMappingOverride(Boolean.valueOf(confMappingOverride).booleanValue());
            }
            checkConfigurations();
        }

        protected void configurationStarted(Attributes attributes) {
            state = State.CONF;
            setDefaultConfMapping(settings
                    .substitute(attributes.getValue("defaultconfmapping")));
            setDefaultConf(settings.substitute(attributes.getValue("defaultconf")));
            getMd()
                    .setMappingOverride(Boolean.valueOf(
                        settings.substitute(attributes.getValue("confmappingoverride")))
                            .booleanValue());
        }

        protected void infoStarted(Attributes attributes) {
            state = State.INFO;
            String org = settings.substitute(attributes.getValue("organisation"));
            String module = settings.substitute(attributes.getValue("module"));
            String revision = settings.substitute(attributes.getValue("revision"));
            String branch = settings.substitute(attributes.getValue("branch"));
            getMd().setModuleRevisionId(ModuleRevisionId.newInstance(org, module, branch,
                revision, ExtendableItemHelper.getExtraAttributes(settings, attributes,
                    new String[] {
                        "organisation", "module", "revision", "status", "publication",
                        "branch", "namespace", "default", "resolver"})));

            String namespace = settings.substitute(attributes.getValue("namespace"));
            if (namespace != null) {
                Namespace ns = settings.getNamespace(namespace);
                if (ns == null) {
                    Message.warn("namespace not found for " + getMd().getModuleRevisionId()
                            + ": " + namespace);
                } else {
                    getMd().setNamespace(ns);
                }
            }

            String status = settings.substitute(attributes.getValue("status"));
            getMd().setStatus(status == null ? settings.getStatusManager().getDefaultStatus()
                    : status);
            getMd().setDefault(Boolean.valueOf(settings.substitute(attributes.getValue("default")))
                    .booleanValue());
            String pubDate = settings.substitute(attributes.getValue("publication"));
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

        protected void ivyModuleStarted(Attributes attributes) throws SAXException {
            descriptorVersion = attributes.getValue("version");
            int versionIndex = ALLOWED_VERSIONS.indexOf(descriptorVersion);
            if (versionIndex == -1) {
                addError("invalid version " + descriptorVersion);
                throw new SAXException("invalid version " + descriptorVersion);
            }
            if (versionIndex >= ALLOWED_VERSIONS.indexOf("1.3")) {
                Message.debug("post 1.3 ivy file: using " + PatternMatcher.EXACT
                        + " as default matcher");
                defaultMatcher = settings.getMatcher(PatternMatcher.EXACT);
            } else {
                Message.debug("pre 1.3 ivy file: using " + PatternMatcher.EXACT_OR_REGEXP
                        + " as default matcher");
                defaultMatcher = settings.getMatcher(PatternMatcher.EXACT_OR_REGEXP);
            }

            for (int i = 0; i < attributes.getLength(); i++) {
                if (attributes.getQName(i).startsWith("xmlns:")) {
                    getMd().addExtraAttributeNamespace(
                        attributes.getQName(i).substring("xmlns:".length()),
                        attributes.getValue(i));
                }
            }
        }

        protected void addDependencyArtifacts(String tag, Attributes attributes)
                throws MalformedURLException {
            state = State.DEP_ARTIFACT;
            parseRule(tag, attributes);
        }

        protected void addIncludeRule(String tag, Attributes attributes)
                throws MalformedURLException {
            state = State.ARTIFACT_INCLUDE;
            parseRule(tag, attributes);
        }

        protected void addExcludeRule(String tag, Attributes attributes)
                throws MalformedURLException {
            state = State.ARTIFACT_EXCLUDE;
            parseRule(tag, attributes);
        }

        protected void parseRule(String tag, Attributes attributes) throws MalformedURLException {
            String name = settings.substitute(attributes.getValue("name"));
            if (name == null) {
                name = settings.substitute(attributes.getValue("artifact"));
                if (name == null) {
                    name = "artifact".equals(tag) ? dd.getDependencyId().getName()
                            : PatternMatcher.ANY_EXPRESSION;
                }
            }
            String type = settings.substitute(attributes.getValue("type"));
            if (type == null) {
                type = "artifact".equals(tag) ? "jar" : PatternMatcher.ANY_EXPRESSION;
            }
            String ext = settings.substitute(attributes.getValue("ext"));
            ext = ext != null ? ext : type;
            if (state == State.DEP_ARTIFACT) {
                String url = settings.substitute(attributes.getValue("url"));
                Map extraAtt = ExtendableItemHelper.getExtraAttributes(settings, attributes,
                    new String[] {"name", "type", "ext", "url", "conf"});
                confAware = new DefaultDependencyArtifactDescriptor(dd, name, type, ext,
                        url == null ? null : new URL(url), extraAtt);
            } else if (state == State.ARTIFACT_INCLUDE) {
                PatternMatcher matcher = getPatternMatcher(attributes.getValue("matcher"));
                String org = settings.substitute(attributes.getValue("org"));
                org = org == null ? PatternMatcher.ANY_EXPRESSION : org;
                String module = settings.substitute(attributes.getValue("module"));
                module = module == null ? PatternMatcher.ANY_EXPRESSION : module;
                ArtifactId aid = new ArtifactId(new ModuleId(org, module), name, type, ext);
                Map extraAtt = ExtendableItemHelper.getExtraAttributes(settings, attributes,
                    new String[] {"org", "module", "name", "type", "ext", "matcher", "conf"});
                confAware = new DefaultIncludeRule(aid, matcher, extraAtt);
            } else { // _state == ARTIFACT_EXCLUDE || EXCLUDE
                PatternMatcher matcher = getPatternMatcher(attributes.getValue("matcher"));
                String org = settings.substitute(attributes.getValue("org"));
                org = org == null ? PatternMatcher.ANY_EXPRESSION : org;
                String module = settings.substitute(attributes.getValue("module"));
                module = module == null ? PatternMatcher.ANY_EXPRESSION : module;
                ArtifactId aid = new ArtifactId(new ModuleId(org, module), name, type, ext);
                Map extraAtt = ExtendableItemHelper.getExtraAttributes(settings, attributes,
                    new String[] {"org", "module", "name", "type", "ext", "matcher", "conf"});
                confAware = new DefaultExcludeRule(aid, matcher, extraAtt);
            }
            String confs = settings.substitute(attributes.getValue("conf"));
            // only add confs if they are specified. if they aren't, endElement will handle this
            // only if there are no conf defined in sub elements
            if (confs != null && confs.length() > 0) {
                String[] conf;
                if ("*".equals(confs)) {
                    conf = getMd().getConfigurationsNames();
                } else {
                    conf = confs.split(",");
                }
                for (int i = 0; i < conf.length; i++) {
                    addConfiguration(conf[i].trim());
                }
            }
        }

        protected void addConfiguration(String c) {
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

        protected PatternMatcher getPatternMatcher(String m) {
            String matcherName = settings.substitute(m);
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
            if (state == State.PUB && "artifact".equals(qName)
                    && artifact.getConfigurations().length == 0) {
                String[] confs = publicationsDefaultConf == null
                    ? getMd().getConfigurationsNames()
                    : publicationsDefaultConf;
                for (int i = 0; i < confs.length; i++) {
                    artifact.addConfiguration(confs[i].trim());
                    getMd().addArtifact(confs[i].trim(), artifact);
                }
            } else if ("configurations".equals(qName)) {
                checkConfigurations();
            } else if ((state == State.DEP_ARTIFACT && "artifact".equals(qName))
                    || (state == State.ARTIFACT_INCLUDE && "include".equals(qName))
                    || (state == State.ARTIFACT_EXCLUDE && "exclude".equals(qName))) {
                state = State.DEP;
                if (confAware.getConfigurations().length == 0) {
                    String[] confs = getMd().getConfigurationsNames();
                    for (int i = 0; i < confs.length; i++) {
                        addConfiguration(confs[i]);
                    }
                }
                confAware = null;
            } else if ("exclude".equals(qName) && state == State.EXCLUDE) {
                if (confAware.getConfigurations().length == 0) {
                    String[] confs = getMd().getConfigurationsNames();
                    for (int i = 0; i < confs.length; i++) {
                        addConfiguration(confs[i]);
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
                    buffer.append("</" + qName + ">");
                }
            }
        }

        protected void checkConfigurations() {
            if (getMd().getConfigurations().length == 0) {
                getMd().addConfiguration(new Configuration("default"));
            }
        }

        protected void replaceConfigurationWildcards() {
            Configuration[] configs = getMd().getConfigurations();
            for (int i = 0; i < configs.length; i++) {
                configs[i].replaceWildcards(getMd());
            }
        }

        /* getters and setters available for extension only */
        protected ParserSettings getSettings() {
            return settings;
        }

        protected URL getDescriptorURL() {
            return descriptorURL;
        }

        protected InputStream getDescriptorInput() {
            return descriptorInput;
        }

        protected int getState() {
            return state;
        }

        protected void setState(int state) {
            this.state = state;
        }

        protected PatternMatcher getDefaultMatcher() {
            return defaultMatcher;
        }

        protected DefaultDependencyDescriptor getDd() {
            return dd;
        }

        protected void setDd(DefaultDependencyDescriptor dd) {
            this.dd = dd;
        }

        protected ConfigurationAware getConfAware() {
            return confAware;
        }

        protected void setConfAware(ConfigurationAware confAware) {
            this.confAware = confAware;
        }

        protected MDArtifact getArtifact() {
            return artifact;
        }

        protected void setArtifact(MDArtifact artifact) {
            this.artifact = artifact;
        }

        protected String getConf() {
            return conf;
        }

        protected void setConf(String conf) {
            this.conf = conf;
        }

        protected boolean isArtifactsDeclared() {
            return artifactsDeclared;
        }

        protected void setArtifactsDeclared(boolean artifactsDeclared) {
            this.artifactsDeclared = artifactsDeclared;
        }

        protected StringBuffer getBuffer() {
            return buffer;
        }

        protected void setBuffer(StringBuffer buffer) {
            this.buffer = buffer;
        }

        protected String getDescriptorVersion() {
            return descriptorVersion;
        }

        protected void setDescriptorVersion(String descriptorVersion) {
            this.descriptorVersion = descriptorVersion;
        }

        protected String[] getPublicationsDefaultConf() {
            return publicationsDefaultConf;
        }

        protected void setPublicationsDefaultConf(String[] publicationsDefaultConf) {
            this.publicationsDefaultConf = publicationsDefaultConf;
        }

        protected boolean isValidate() {
            return validate;
        }

        protected URL getSchemaURL() {
            return getClass().getResource("ivy.xsd");
        }
    }

    public String toString() {
        return "ivy parser";
    }
}