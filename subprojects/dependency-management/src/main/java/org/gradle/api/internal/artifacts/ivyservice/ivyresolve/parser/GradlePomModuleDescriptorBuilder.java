/*
 * Copyright 2011 the original author or authors.
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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.dependencies.DefaultImmutableVersionConstraint;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.PomReader.PomDependencyData;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.data.PomDependencyMgt;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.PatternMatchers;
import org.gradle.internal.component.external.descriptor.Artifact;
import org.gradle.internal.component.external.descriptor.Configuration;
import org.gradle.internal.component.external.descriptor.DefaultExclude;
import org.gradle.internal.component.external.descriptor.MavenScope;
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier;
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector;
import org.gradle.internal.component.external.model.IvyDependencyMetadata;
import org.gradle.internal.component.external.model.MavenDependencyMetadata;
import org.gradle.internal.component.external.model.ModuleDependencyMetadata;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.Exclude;
import org.gradle.internal.component.model.IvyArtifactName;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This a straight copy of org.apache.ivy.plugins.parser.m2.PomModuleDescriptorBuilder, with minor changes: 1) Do not create artifact for empty classifier. (Previously did so for all non-null
 * classifiers)
 */
public class GradlePomModuleDescriptorBuilder {
    public static final Map<String, Configuration> MAVEN2_CONFIGURATIONS = ImmutableMap.<String, Configuration>builder()
        .put("default", new Configuration("default", true, true, ImmutableSet.of("runtime", "master")))
        .put("master", new Configuration("master", true, true, ImmutableSet.<String>of()))
        .put("compile", new Configuration("compile", true, true, ImmutableSet.<String>of()))
        .put("provided", new Configuration("provided", true, true, ImmutableSet.<String>of()))
        .put("runtime", new Configuration("runtime", true, true, ImmutableSet.of("compile")))
        .put("test", new Configuration("test", true, false, ImmutableSet.of("runtime")))
        .put("system", new Configuration("system", true, true, ImmutableSet.<String>of()))
        .put("sources", new Configuration("sources", true, true, ImmutableSet.<String>of()))
        .put("javadoc", new Configuration("javadoc", true, true, ImmutableSet.<String>of()))
        .put("optional", new Configuration("optional", true, true, ImmutableSet.<String>of())).build();

    private static final Map<String, MavenScope> SCOPES = ImmutableMap.<String, MavenScope>builder()
        .put("compile", MavenScope.Compile)
        .put("runtime", MavenScope.Runtime)
        .put("provided", MavenScope.Provided)
        .put("test", MavenScope.Test)
        .put("system", MavenScope.System)
        .build();
    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile("(.+)-\\d{8}\\.\\d{6}-\\d+");
    private static final String[] WILDCARD = new String[]{"*"};

    private final VersionSelectorScheme defaultVersionSelectorScheme;
    private final VersionSelectorScheme mavenVersionSelectorScheme;

    private List<ModuleDependencyMetadata> dependencies = Lists.newArrayList();
    private final PomReader pomReader;
    private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;
    private String status;
    private ModuleComponentIdentifier componentIdentifier;

    public GradlePomModuleDescriptorBuilder(PomReader pomReader, VersionSelectorScheme gradleVersionSelectorScheme, VersionSelectorScheme mavenVersionSelectorScheme, ImmutableModuleIdentifierFactory moduleIdentifierFactory) {
        this.defaultVersionSelectorScheme = gradleVersionSelectorScheme;
        this.mavenVersionSelectorScheme = mavenVersionSelectorScheme;
        this.pomReader = pomReader;
        this.moduleIdentifierFactory = moduleIdentifierFactory;
    }

    public List<ModuleDependencyMetadata> getDependencies() {
        return dependencies;
    }

    public String getStatus() {
        return status;
    }

    public ModuleComponentIdentifier getComponentIdentifier() {
        return componentIdentifier;
    }

    public void setModuleRevId(String group, String module, String version) {
        String effectiveVersion = version;
        if (version != null) {
            Matcher matcher = TIMESTAMP_PATTERN.matcher(version);
            if (matcher.matches()) {
                effectiveVersion = matcher.group(1) + "-SNAPSHOT";
            }
        }

        status = effectiveVersion != null && effectiveVersion.endsWith("SNAPSHOT") ? "integration" : "release";
        componentIdentifier = DefaultModuleComponentIdentifier.newId(group, module, effectiveVersion);
    }

    public void addDependency(PomDependencyData dep) {
        doAddDependency(dep, dep.isOptional());
    }

    public void addOptionalDependency(PomDependencyMgt dep) {
        doAddDependency(dep, true);
    }

    private void doAddDependency(PomDependencyMgt dep, boolean optional) {
        String scopeString = dep.getScope();
        if (scopeString == null || scopeString.length() == 0) {
            scopeString = getDefaultScope(dep);
        }

        MavenScope scope;
        if (SCOPES.containsKey(scopeString)) {
            scope = SCOPES.get(scopeString);
        } else {
            // unknown scope, defaulting to 'compile'
            scope = MavenScope.Compile;
        }

        String version = determineVersion(dep, optional);
        String mappedVersion = convertVersionFromMavenSyntax(version);
        ModuleComponentSelector selector = DefaultModuleComponentSelector.newSelector(dep.getGroupId(), dep.getArtifactId(), new DefaultImmutableVersionConstraint(mappedVersion));

        // Some POMs depend on themselves, don't add this dependency: Ivy doesn't allow this!
        // Example: http://repo2.maven.org/maven2/net/jini/jsk-platform/2.1/jsk-platform-2.1.pom
        if (selector.getGroup().equals(componentIdentifier.getGroup())
            && selector.getModule().equals(componentIdentifier.getModule())) {
            return;
        }

        List<Artifact> artifacts = Lists.newArrayList();
        boolean hasClassifier = dep.getClassifier() != null && dep.getClassifier().length() > 0;
        boolean hasNonJarType = dep.getType() != null && !"jar".equals(dep.getType());
        if (hasClassifier || hasNonJarType) {
            String type = "jar";
            if (dep.getType() != null) {
                type = dep.getType();
            }
            String ext = determineExtension(type);
            String classifier = hasClassifier ? dep.getClassifier() : getClassifierForType(type);

            // here we have to assume a type and ext for the artifact, so this is a limitation
            // compared to how m2 behave with classifiers
            String optionalizedScope = optional ? "optional" : scope.toString().toLowerCase();

            IvyArtifactName artifactName = new DefaultIvyArtifactName(selector.getModule(), type, ext, classifier);
            artifacts.add(new Artifact(artifactName, Collections.singleton(optionalizedScope)));
        }

        // experimentation shows the following, excluded modules are
        // inherited from parent POMs if either of the following is true:
        // the <exclusions> element is missing or the <exclusions> element
        // is present, but empty.
        List<Exclude> excludes = Lists.newArrayList();
        List<ModuleIdentifier> excluded = dep.getExcludedModules();
        if (excluded.isEmpty()) {
            excluded = getDependencyMgtExclusions(dep);
        }
        for (ModuleIdentifier excludedModule : excluded) {
            DefaultExclude rule = new DefaultExclude(
                moduleIdentifierFactory.module(excludedModule.getGroup(), excludedModule.getName()),
                WILDCARD,
                PatternMatchers.EXACT);
            excludes.add(rule);
        }

        dependencies.add(new MavenDependencyMetadata(scope, optional, selector, artifacts, excludes));
    }

    private String convertVersionFromMavenSyntax(String version) {
        VersionSelector versionSelector = mavenVersionSelectorScheme.parseSelector(version);
        return defaultVersionSelectorScheme.renderSelector(versionSelector);
    }

    /**
     * Determines extension of dependency.
     *
     * @param type Type
     * @return Extension
     */
    private String determineExtension(String type) {
        return JarDependencyType.isJarExtension(type) ? "jar" : type;
    }

    /**
     * Handles special types of dependencies. If one of the following types matches, a specific type of classifier is set.
     *
     * - test-jar (see <a href="http://maven.apache.org/guides/mini/guide-attached-tests.html">Maven documentation</a>)
     * - ejb-client (see <a href="http://maven.apache.org/plugins/maven-ejb-plugin/examples/ejb-client-dependency.html">Maven documentation</a>)
     *
     * @param type Type
     */
    private String getClassifierForType(String type) {
        if(JarDependencyType.TEST_JAR.getName().equals(type)) {
            return "tests";
        } else if(JarDependencyType.EJB_CLIENT.getName().equals(type)) {
            return "client";
        }
        return null;
    }

    private enum JarDependencyType {
        TEST_JAR("test-jar"), EJB_CLIENT("ejb-client"), EJB("ejb"), BUNDLE("bundle"), MAVEN_PLUGIN("maven-plugin"), ECLIPSE_PLUGIN("eclipse-plugin");

        private static final Map<String, JarDependencyType> TYPES;

        static {
            TYPES = new HashMap<String, JarDependencyType>();

            for(JarDependencyType type : values()) {
                TYPES.put(type.name, type);
            }
        }

        private final String name;

        private JarDependencyType(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public static boolean isJarExtension(String type) {
            return TYPES.containsKey(type);
        }
    }

    /**
     * Determines the version of a dependency. Uses the specified version if declared for the as coordinate. If the version is not declared, try to resolve it from the dependency management section.
     * In case the version cannot be resolved with any of these methods:
     * - If this is a direct dependency: throw an exception of type {@see org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.UnresolvedDependencyVersionException}.
     * - If this is an optional dependency: return the empty version
     *
     * @param dependency Dependency
     * @param optional is this an optional dependency?
     * @return Resolved dependency version
     */
    private String determineVersion(PomDependencyMgt dependency, boolean optional) {
        String version = dependency.getVersion();
        version = (version == null || version.length() == 0) ? getDefaultVersion(dependency) : version;

        if (version == null) {
            if (optional) {
                version = "";
            } else {
                throw new UnresolvedDependencyVersionException(dependency.getId());
            }
        }

        return version;
    }

    public void addDependencyForRelocation(ModuleComponentSelector selector) {

        // Some POMs depend on themselves through their parent POM, don't add this dependency
        // since Ivy doesn't allow this!
        // Example: http://repo2.maven.org/maven2/com/atomikos/atomikos-util/3.6.4/atomikos-util-3.6.4.pom
        if (selector.getGroup().equals(componentIdentifier.getGroup())
            && selector.getModule().equals(componentIdentifier.getModule())) {
            return;
        }

        // TODO - this is a constant
        ListMultimap<String, String> confMappings = ArrayListMultimap.create();
        // Map dependency on all public configurations
        for (Configuration m2Conf : GradlePomModuleDescriptorBuilder.MAVEN2_CONFIGURATIONS.values()) {
            if (m2Conf.isVisible()) {
                confMappings.put(m2Conf.getName(), m2Conf.getName());
            }
        }

        dependencies.add(new IvyDependencyMetadata(selector, confMappings));
    }

    private String getDefaultVersion(PomDependencyMgt dep) {
        PomDependencyMgt pomDependencyMgt = findDependencyDefault(dep);
        if (pomDependencyMgt != null) {
            return pomDependencyMgt.getVersion();
        }
        return null;
    }

    private String getDefaultScope(PomDependencyMgt dep) {
        PomDependencyMgt pomDependencyMgt = findDependencyDefault(dep);
        String result = null;
        if (pomDependencyMgt != null) {
            result = pomDependencyMgt.getScope();
        }
        if ((result == null) || !SCOPES.containsKey(result)) {
            result = "compile";
        }
        return result;
    }

    private List<ModuleIdentifier> getDependencyMgtExclusions(PomDependencyMgt dep) {
        PomDependencyMgt pomDependencyMgt = findDependencyDefault(dep);
        if (pomDependencyMgt != null) {
            return pomDependencyMgt.getExcludedModules();
        }

        return Collections.emptyList();
    }

    private PomDependencyMgt findDependencyDefault(PomDependencyMgt dependency) {
        return pomReader.findDependencyDefaults(dependency.getId());
    }
}
