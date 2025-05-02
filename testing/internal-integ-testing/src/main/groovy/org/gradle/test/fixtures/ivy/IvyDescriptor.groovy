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

package org.gradle.test.fixtures.ivy

import groovy.namespace.QName
import groovy.xml.XmlParser
import org.apache.commons.lang.StringUtils

class IvyDescriptor {
    Map<String, IvyDescriptorConfiguration> configurations = [:]
    List<IvyDescriptorArtifact> artifacts = []
    Map<String, IvyDescriptorDependency> dependencies = [:]
    List<IvyDescriptorDependencyExclusion> exclusions = []
    String organisation
    String module
    String revision
    String status
    Node description
    String branch
    String resolver
    NodeList licenses
    NodeList authors
    Map<QName, String> extraInfo

    IvyDescriptor(File ivyFile) {
        def ivy = new XmlParser().parse(ivyFile)
        organisation = ivy.info[0].@organisation
        module = ivy.info[0].@module
        revision = ivy.info[0].@revision
        status = ivy.info[0].@status
        branch = ivy.info[0].@branch
        resolver = ivy.info[0].@resolver
        description = ivy.info[0].description[0]
        licenses = ivy.info[0].license
        authors = ivy.info[0].ivyauthor

        extraInfo = [:]
        ivy.info[0].children().findAll { it.name() instanceof QName }.each {
            extraInfo[new javax.xml.namespace.QName(it.name().namespaceURI, it.name().localPart)] = it.text()
        }

        ivy.configurations.conf.each {
            configurations[it.@name] = new IvyDescriptorConfiguration(
                    name: it.@name, visibility: it.@visibility, description: it.@description,
                    extend: it.@extends == null ? null : it.@extends.split(",")*.trim()
            )
        }

        ivy.publications.artifact.each { artifact ->
            def ivyArtifact = new IvyDescriptorArtifact(
                    name: artifact.@name, type: artifact.@type,
                    ext: artifact.@ext,
                    conf: artifact.@conf == null ? null : artifact.@conf.split(",") as List,
                    mavenAttributes: artifact.attributes().findAll { it.key instanceof QName && it.key.namespaceURI == "http://ant.apache.org/ivy/maven" }.collectEntries { [it.key.localPart, it.value] }
            )

            artifacts.add(ivyArtifact)
        }


        ivy.dependencies.dependency.each { dep ->
            def ivyDependency = new IvyDescriptorDependency(
                    org: dep.@org,
                    module: dep.@name,
                    revision: dep.@rev,
                    revisionConstraint: dep.@revConstraint,
                    confs: [dep.@conf],
                    transitive: dep.@transitive
            )

            dep.exclude.each { exclude ->
                ivyDependency.exclusions << new IvyDescriptorDependencyExclusion(org: exclude.@org, module: exclude.@module, name: exclude.@name, type: exclude.@type, ext: exclude.@ext, conf: exclude.@conf, matcher: exclude.@matcher)
            }

            def key = "${ivyDependency.org}:${ivyDependency.module}:${ivyDependency.revision}"
            if (dependencies[key]) {
                dependencies[key].confs += ivyDependency.confs
            } else {
                dependencies[key] = ivyDependency
            }
        }

        ivy.dependencies.exclude.each { exclude ->
            exclusions << new IvyDescriptorDependencyExclusion(org: exclude.@org, module: exclude.@module, name: exclude.@artifact, type: exclude.@type, ext: exclude.@ext, conf: exclude.@conf, matcher: exclude.@matcher)
        }
    }

    IvyDescriptorArtifact expectArtifact(String name, String ext, String classifier = null) {
        return oneResult(artifacts.findAll({
            it.name == name && it.ext == ext && it.classifier == classifier
        }), [name, ext, classifier])
    }

    IvyDescriptorArtifact expectArtifact(String name) {
        return oneResult(artifacts.findAll({
            it.name == name && it.ext != 'module'
        }), [name])
    }

    private static IvyDescriptorArtifact oneResult(List<IvyDescriptorArtifact> artifacts, def description) {
        assert artifacts.size() > 0 : "Expected artifact not found: $description"
        assert artifacts.size() == 1 : "Multiple artifacts found: $description"
        return artifacts.get(0)
    }

    def assertDependsOn(String[] expected) {
        assert dependencies.size() == expected.length
        expected.each {
            String key = StringUtils.substringBefore(it, "@")
            String conf = StringUtils.substringAfter(it, "@") + "->default"
            assert dependencies.containsKey(key)
            assert dependencies[key].hasConf(conf)
        }
        true
    }

    def assertConfigurationDependsOn(String configuration, String[] expected) {
        def actualDependencies = dependencies.values().findAll { it.confs.any { it.contains(configuration) }}
        assert actualDependencies.size() == expected.length
        expected.each {
            String conf = "$configuration->default"
            assert dependencies.containsKey(it)
            assert dependencies[it].hasConf(conf)
        }

        true
    }

    IvyDescriptorDependency expectDependency(String key) {
        final dependency = dependencies.get(key)
        assert dependency != null : "Could not find expected dependency $key. Actual: ${dependencies.values()}"
        return dependency
    }
}
