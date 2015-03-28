/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.publication.maven.internal.pom
import org.apache.maven.model.Dependency
import org.apache.maven.model.Exclusion
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.artifacts.ExcludeRule
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.maven.Conf2ScopeMapping
import org.gradle.api.artifacts.maven.Conf2ScopeMappingContainer
import org.gradle.api.internal.artifacts.DefaultExcludeRule
import org.gradle.api.internal.artifacts.dependencies.DefaultDependencyArtifact
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency
import org.gradle.api.publication.maven.internal.VersionRangeMapper
import spock.lang.Issue
import spock.lang.Specification

import static org.gradle.util.WrapUtil.*

public class DefaultPomDependenciesConverterTest extends Specification {
    
    private DefaultPomDependenciesConverter dependenciesConverter;
    private conf2ScopeMappingContainerMock = Mock(Conf2ScopeMappingContainer)
    private excludeRuleConverterMock = Mock(ExcludeRuleConverter)
    private versionRangeMapper = Mock(VersionRangeMapper)

    private ModuleDependency dependency1
    private ModuleDependency dependency2
    private ModuleDependency dependency31
    private ModuleDependency dependency32
    private Configuration compileConfStub
    private Configuration testCompileConfStub

    public void setup() {
        setUpCommonDependenciesAndConfigurations();
        dependenciesConverter = new DefaultPomDependenciesConverter(excludeRuleConverterMock, versionRangeMapper);
    }

    private void setUpCommonDependenciesAndConfigurations() {
        dependency1 = createDependency("org1", "name1", "rev1");
        dependency2 = createDependency("org2", "name2", "rev2");
        dependency2.addArtifact(new DefaultDependencyArtifact("name2", null, null, null, null));
        dependency31 = createDependency("org3", "name3", "rev3");
        dependency32 = createDependency("org3", "name3", "rev3");
        dependency32.addArtifact(new DefaultDependencyArtifact("artifactName32", "type32", "ext", "classifier32", null));
        compileConfStub = createNamedConfigurationStubWithDependencies("compile", dependency1, dependency31);
        testCompileConfStub = createNamedConfigurationStubWithDependencies("testCompile", dependency2, dependency32);

        _ * conf2ScopeMappingContainerMock.getMapping(toSet(testCompileConfStub, compileConfStub)) >> createMapping(testCompileConfStub, "test")
        _ * conf2ScopeMappingContainerMock.getMapping(toSet(compileConfStub, testCompileConfStub)) >> createMapping(testCompileConfStub, "test")
        _ * conf2ScopeMappingContainerMock.getMapping(toSet(testCompileConfStub)) >> createMapping(testCompileConfStub, "test")
        _ * conf2ScopeMappingContainerMock.getMapping(toSet(compileConfStub)) >> createMapping(compileConfStub, "compile")
        _ * versionRangeMapper.map("rev1") >> "rev1"
        _ * versionRangeMapper.map("rev2") >> "rev2"
        _ * versionRangeMapper.map("rev3") >> "rev3"
    }

    private static createMapping(Configuration configuration, String scope) {
        return new Conf2ScopeMapping(10, configuration, scope);
    }

    private Configuration createNamedConfigurationStubWithDependencies(final String confName, final ModuleDependency... dependencies) {
        return createNamedConfigurationStubWithDependencies(confName, new HashSet<ExcludeRule>(), dependencies);
    }
    
    private Configuration createNamedConfigurationStubWithDependencies(final String confName, final Set<ExcludeRule> excludeRules, final ModuleDependency... dependencies) {
        final DependencySet dependencySet = Stub(DependencySet) {
            withType(ModuleDependency) >> toDomainObjectSet(ModuleDependency, dependencies)
        }
        final Configuration configurationStub = Stub(Configuration) {
            getName() >> confName
            getDependencies() >> dependencySet
            getExcludeRules() >> excludeRules
        }
        return configurationStub;
    }

    private static createDependency(final String group, final String name, final String version) {
        return new DefaultExternalModuleDependency(group, name, version);
    }

    def init() {
        expect:
        excludeRuleConverterMock == dependenciesConverter.getExcludeRuleConverter()
    }

    def convert() {
        Set<Configuration> configurations = toSet(compileConfStub, testCompileConfStub);

        when:
        def actualMavenDependencies = dependenciesConverter.convert(conf2ScopeMappingContainerMock, configurations)

        then:
        actualMavenDependencies.size() == 4
        checkCommonMavenDependencies(actualMavenDependencies)
    }

    @Issue("https://issues.gradle.org/browse/GRADLE-3233")
    def convertsDependencyWithNullVersion() {
        def dependency4 = createDependency("org4", "name4", null)

        when:
        def stubbedConfiguration = createNamedConfigurationStubWithDependencies("conf", dependency4)
        _ * conf2ScopeMappingContainerMock.getMapping(toSet(stubbedConfiguration)) >> createMapping(stubbedConfiguration, null)

        then:
        def  actualMavenDependencies = dependenciesConverter.convert(conf2ScopeMappingContainerMock, toSet(compileConfStub, testCompileConfStub, stubbedConfiguration));

        and:
        actualMavenDependencies.find { it.artifactId == "name4" }.version == null
    }

    def convertWithUnMappedConfAndSkipTrue() {
        def dependency4 = createDependency("org4", "name4", "rev4")

        when:
        def unmappedConfigurationStub = createNamedConfigurationStubWithDependencies("unmappedConf", dependency4)
        _ * conf2ScopeMappingContainerMock.skipUnmappedConfs >> true
        _ * conf2ScopeMappingContainerMock.getMapping(toSet(unmappedConfigurationStub)) >> createMapping(unmappedConfigurationStub, null)

        then:
        def  actualMavenDependencies = dependenciesConverter.convert(conf2ScopeMappingContainerMock, toSet(compileConfStub, testCompileConfStub, unmappedConfigurationStub));

        and:
        actualMavenDependencies.size() == 4
        checkCommonMavenDependencies(actualMavenDependencies);
    }

    def convertWithUnMappedConfAndSkipFalse() {
        final ModuleDependency dependency4 = createDependency("org4", "name4", "rev4")

        when:
        def unmappedConfigurationStub = createNamedConfigurationStubWithDependencies("unmappedConf", dependency4)
        _ * conf2ScopeMappingContainerMock.skipUnmappedConfs >> false
        _ * conf2ScopeMappingContainerMock.getMapping(toSet(unmappedConfigurationStub)) >> createMapping(unmappedConfigurationStub, null)
        1 * versionRangeMapper.map("rev4") >> "rev4"

        then:
        def  actualMavenDependencies = dependenciesConverter.convert(conf2ScopeMappingContainerMock, toSet(compileConfStub, testCompileConfStub, unmappedConfigurationStub));

        and:
        actualMavenDependencies.size() == 5
        checkCommonMavenDependencies(actualMavenDependencies);
        hasDependency(actualMavenDependencies, "org4", "name4", "rev4", null, null, null, false)
    }

    def convertWithConvertibleDependencyExcludes() {
        when:
        def someConfigurationStub = createNamedConfigurationStubWithDependencies("someConfiguration", dependency1)
        def mavenExclude = new Exclusion()
        mavenExclude.groupId = "a"
        mavenExclude.artifactId = "b"

        dependency1.exclude(toMap(ExcludeRule.GROUP_KEY, "value"))

        _ * conf2ScopeMappingContainerMock.getMapping(toSet(someConfigurationStub)) >> createMapping(compileConfStub, "compile")
        _ * excludeRuleConverterMock.convert(dependency1.excludeRules.iterator().next()) >> mavenExclude

        then:
        def actualMavenDependencies = dependenciesConverter.convert(conf2ScopeMappingContainerMock, toSet(someConfigurationStub))

        and:
        actualMavenDependencies.size() == 1
        hasDependency(actualMavenDependencies, "org1", "name1", "rev1", null, "compile", null, false)

        def mavenDependency = actualMavenDependencies.get(0);
        mavenDependency.exclusions.size() == 1
        mavenDependency.exclusions[0].groupId == "a"
        mavenDependency.exclusions[0].artifactId == "b"
    }

    def convertWithConvertibleConfigurationExcludes() {
        when:
        def excludeRule = new DefaultExcludeRule("value", null)
        def someConfigurationStub = createNamedConfigurationStubWithDependencies("someConfiguration", toSet(excludeRule), dependency1)
        def mavenExclude = new Exclusion()
        mavenExclude.groupId = "a"
        mavenExclude.artifactId = "b"

        _ * conf2ScopeMappingContainerMock.getMapping(toSet(someConfigurationStub)) >> createMapping(compileConfStub, "compile")
        _ * excludeRuleConverterMock.convert(excludeRule) >> mavenExclude

        then:
        def actualMavenDependencies = dependenciesConverter.convert(conf2ScopeMappingContainerMock, toSet(someConfigurationStub))

        and:
        actualMavenDependencies.size() == 1
        hasDependency(actualMavenDependencies, "org1", "name1", "rev1", null, "compile", null, false)

        def mavenDependency = actualMavenDependencies.get(0);
        mavenDependency.exclusions.size() == 1
        mavenDependency.exclusions[0].groupId == "a"
        mavenDependency.exclusions[0].artifactId == "b"
    }

    private static void checkCommonMavenDependencies(List<Dependency> actualMavenDependencies) {
        assert hasDependency(actualMavenDependencies, "org1", "name1", "rev1", null, "compile", null, false)
        assert hasDependency(actualMavenDependencies, "org2", "name2", "rev2", null, "test", null, false)
        assert hasDependency(actualMavenDependencies, "org3", "name3", "rev3", null, "test", null, false)
        assert hasDependency(actualMavenDependencies, "org3", "artifactName32", "rev3", "type32", "test", "classifier32", false)
    }

    private static hasDependency(List<Dependency> mavenDependencies,
                                  String group, String artifactId, String version, String type, String scope,
                                  String classifier, boolean optional) {
        Dependency expectedDependency = new Dependency();
        expectedDependency.setGroupId(group);
        expectedDependency.setArtifactId(artifactId);
        expectedDependency.setVersion(version);
        expectedDependency.setType(type);
        expectedDependency.setScope(scope);
        expectedDependency.setClassifier(classifier);
        expectedDependency.setOptional(optional);
        for (Dependency mavenDependency : mavenDependencies) {
            if (equals(mavenDependency, expectedDependency)) {
                return true;
            }
        }
        return false;
    }

    private static boolean equals(Dependency lhs, Dependency rhs) {
        if (!lhs.getGroupId().equals(lhs.getGroupId())) {
            return false;
        }
        if (!lhs.getArtifactId().equals(lhs.getArtifactId())) {
            return false;
        }
        if (!lhs.getVersion().equals(lhs.getVersion())) {
            return false;
        }
        if (lhs.getType() != null ? !lhs.getType().equals(lhs.getType()) : rhs.getType() != null) {
            return false;
        }
        if (lhs.getScope() != null ? !lhs.getScope().equals(lhs.getScope()) : rhs.getScope() != null) {
            return false;
        }
        if (!lhs.isOptional() == lhs.isOptional()) {
            return false;
        }
        if (lhs.getClassifier() != null ? !lhs.getClassifier().equals(rhs.getClassifier()) : rhs.getClassifier() != null) {
            return false;
        }
        return true;
    }
}
