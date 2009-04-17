/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.api.internal.artifacts;

import groovy.lang.Closure;
import org.apache.ivy.plugins.resolver.AbstractResolver;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.apache.ivy.plugins.resolver.FileSystemResolver;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Task;
import org.gradle.api.artifacts.ResolverContainer;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.maven.Conf2ScopeMappingContainer;
import org.gradle.api.artifacts.maven.GroovyMavenDeployer;
import org.gradle.api.artifacts.maven.MavenResolver;
import org.gradle.api.internal.ConventionAwareHelper;
import org.gradle.api.internal.artifacts.ivyservice.ResolverFactory;
import org.gradle.api.plugins.Convention;
import org.gradle.api.tasks.ConventionValue;
import org.gradle.util.ConfigureUtil;
import org.gradle.util.GUtil;
import org.gradle.util.HashUtil;
import org.gradle.util.WrapUtil;

import java.io.File;
import java.util.*;

/**
 * @author Hans Dockter
 */
public class DefaultResolverContainer implements ResolverContainer {
    private ConventionAwareHelper conventionAwareHelper;

    private ResolverFactory resolverFactory;

    private List<String> resolverNames = new ArrayList<String>();

    private Map<String, DependencyResolver> resolvers = new HashMap<String, DependencyResolver>();

    private File mavenPomDir = null;

    private Conf2ScopeMappingContainer mavenScopeMappings = null;

    private ConfigurationContainer configurationContainer = null;

    public DefaultResolverContainer(ResolverFactory resolverFactory, Convention convention) {
        this.resolverFactory = resolverFactory;
        conventionAwareHelper = new ConventionAwareHelper(this);
        conventionAwareHelper.setConvention(convention);
    }

    public DependencyResolver add(Object userDescription) {
        return add(userDescription, null);
    }

    public DependencyResolver add(Object userDescription, Closure configureClosure) {
        return addInternal(userDescription, configureClosure, new OrderAction() {
            public void apply(String resolverName) {
                resolverNames.add(resolverName);
            }
        });
    }

    public DependencyResolver addBefore(Object userDescription, final String afterResolverName) {
        return addBefore(userDescription, afterResolverName, null);
    }

    public DependencyResolver addBefore(Object userDescription, final String afterResolverName, Closure configureClosure) {
        if (!GUtil.isTrue(afterResolverName)) {
            throw new InvalidUserDataException(
                    "You must specify userDescription and afterResolverName");
        }
        if (!GUtil.isTrue(resolvers.get(afterResolverName))) {
            throw new InvalidUserDataException(
                    "Resolver $afterResolverName does not exists!");
        }
        return addInternal(userDescription, configureClosure, new OrderAction() {
            public void apply(String resolverName) {
                resolverNames.add(resolverNames.indexOf(afterResolverName), resolverName);
            }
        });
    }

    public DependencyResolver addAfter(Object userDescription, final String beforeResolverName) {
        return addAfter(userDescription, beforeResolverName, null);
    }

    public DependencyResolver addAfter(Object userDescription, final String beforeResolverName, Closure configureClosure) {
        if (!GUtil.isTrue(beforeResolverName)) {
            throw new InvalidUserDataException(
                    "You must specify userDescription and beforeResolverName");
        }
        if (!GUtil.isTrue(resolvers.get(beforeResolverName))) {
            throw new InvalidUserDataException(
                    "Resolver $beforeResolverName does not exists!");
        }
        return addInternal(userDescription, configureClosure, new OrderAction() {
            public void apply(String resolverName) {
                int insertPos = resolverNames.indexOf(beforeResolverName) + 1;
                if (insertPos == resolverNames.size()) {
                    resolverNames.add(resolverName);
                } else {
                    resolverNames.add(insertPos, resolverName);
                }
            }
        });
    }

    public DependencyResolver addFirst(Object userDescription) {
        return addFirst(userDescription, null);
    }

    public DependencyResolver addFirst(Object userDescription, Closure configureClosure) {
        if (!GUtil.isTrue(userDescription)) {
            throw new InvalidUserDataException("You must specify userDescription");
        }
        return addInternal(userDescription, configureClosure, new OrderAction() {
            public void apply(String resolverName) {
                if (resolverNames.size() == 0) {
                    resolverNames.add(resolverName);
                } else {
                    resolverNames.add(0, resolverName);
                }
            }
        });
    }

    private DependencyResolver addInternal(Object userDescription, Closure configureClosure, OrderAction orderAction) {
        if (!GUtil.isTrue(userDescription)) {
            throw new InvalidUserDataException("You must specify userDescription");
        }
        DependencyResolver resolver = resolverFactory.createResolver(userDescription);
        ConfigureUtil.configure(configureClosure, resolver);
        if (!GUtil.isTrue(resolver.getName())) {
            throw new InvalidUserDataException("You must specify a name for the resolver. Resolver=" + userDescription);
        }
        resolvers.put(resolver.getName(), resolver);
        orderAction.apply(resolver.getName());
        return resolver;
    }


    public DependencyResolver resolver(String name) {
        return resolvers.get(name);
    }

    public List<DependencyResolver> getResolverList() {
        List<DependencyResolver> returnedResolvers = new ArrayList<DependencyResolver>();
        for (String resolverName : resolverNames) {
            returnedResolvers.add(resolvers.get(resolverName));
        }
        return returnedResolvers;
    }

    public FileSystemResolver flatDir(Map args) {
        Object[] rootDirs = getFlatDirRootDirs(args);
        FileSystemResolver resolver = createFlatDirResolver(
                getNameFromMap(args, HashUtil.createHash(GUtil.join(rootDirs, ""))),
                rootDirs);
        return (FileSystemResolver) add(resolver);
    }

    private String getNameFromMap(Map args, String defaultName) {
        return GUtil.isTrue(args.get("name")) ? args.get("name").toString() : defaultName;
    }

    private Object[] getFlatDirRootDirs(Map args) {
        List dirs = createStringifiedListFromMapArg(args, "dirs");
        if (dirs == null) {
            throw new InvalidUserDataException("You must specify dirs for the flat dir repository.");    
        }
        ;
        return dirs.toArray();
    }

    private List<String> createStringifiedListFromMapArg(Map args, String argName) {
        Object dirs = args.get(argName);
        if (dirs == null) {
            return null;
        }
        Iterable<Object> iterable = null;
        if (dirs instanceof Iterable) {
            iterable = (Iterable<Object>) dirs;
        } else {
            iterable = WrapUtil.toSet(dirs);
        }
        List list = new ArrayList();
        for (Object o : iterable) {
            list.add(o.toString());
        }
        return list;
    }

    public DependencyResolver mavenCentral() {
        return mavenCentral(Collections.emptyMap());
    }

    public DependencyResolver mavenCentral(Map args) {
        List<String> urls = createStringifiedListFromMapArg(args, "urls");
        return add(createMavenRepoResolver(
                getNameFromMap(args, DEFAULT_MAVEN_CENTRAL_REPO_NAME),
                MAVEN_CENTRAL_URL,
                urls == null ? WrapUtil.<String>toArray() : urls.toArray(new String[urls.size()])));
    }

    public DependencyResolver mavenRepo(Map args) {
        List<String> urls = createStringifiedListFromMapArg(args, "urls");
        if (urls == null) {
            throw new InvalidUserDataException("You must specify a urls for a Maven repo.");
        }
        return add(createMavenRepoResolver(
                getNameFromMap(args, urls.get(0).toString()),
                urls.get(0).toString(),
                urls.size() == 1 ? WrapUtil.<String>toArray() : urls.subList(1, urls.size()).toArray(new String[urls.size() - 1])));
    }

    public FileSystemResolver createFlatDirResolver(String name, Object... dirs) {
        List<File> dirFiles = new ArrayList<File>();
        for (Object dir : dirs) {
            dirFiles.add(new File(dir.toString()));
        }
        return resolverFactory.createFlatDirResolver(name, dirFiles.toArray(new File[dirFiles.size()]));
    }

    public AbstractResolver createMavenRepoResolver(String name, String root, String[] jarRepoUrls) {
        return resolverFactory.createMavenRepoResolver(name, root, jarRepoUrls);
    }

    private static interface OrderAction {
        void apply(String resolverName);
    }

    public ResolverFactory getResolverFactory() {
        return resolverFactory;
    }

    public void setResolverFactory(ResolverFactory resolverFactory) {
        this.resolverFactory = resolverFactory;
    }

    public List<String> getResolverNames() {
        return resolverNames;
    }

    public void setResolverNames(List<String> resolverNames) {
        this.resolverNames = resolverNames;
    }

    public Map<String, DependencyResolver> getResolvers() {
        return resolvers;
    }

    public void setResolvers(Map<String, DependencyResolver> resolvers) {
        this.resolvers = resolvers;
    }

    public ConfigurationContainer getConfigurationContainer() {
        return (ConfigurationContainer) conv(configurationContainer, "configurationContainer");
    }

    public void setConfigurationContainer(ConfigurationContainer configurationContainer) {
        this.configurationContainer = configurationContainer;
    }

    public GroovyMavenDeployer createMavenDeployer(String name) {
        return resolverFactory.createMavenDeployer(name, getMavenPomDir(), getConfigurationContainer(), getMavenScopeMappings());
    }

    public GroovyMavenDeployer addMavenDeployer(String name) {
        return (GroovyMavenDeployer) add(createMavenDeployer(name));
    }

    public GroovyMavenDeployer addMavenDeployer(String name, Closure configureClosure) {
        return (GroovyMavenDeployer) add(createMavenDeployer(name), configureClosure);
    }

    public MavenResolver createMavenInstaller(String name) {
        return resolverFactory.createMavenInstaller(name, getMavenPomDir(), getConfigurationContainer(), getMavenScopeMappings());
    }

    public MavenResolver addMavenInstaller(String name) {
        return (MavenResolver) add(createMavenInstaller(name));
    }

    public MavenResolver addMavenInstaller(String name, Closure configureClosure) {
        return (MavenResolver) add(createMavenInstaller(name), configureClosure);
    }

    public Task conventionMapping(Map<String, ConventionValue> mapping) {
        return (Task) conventionAwareHelper.conventionMapping(mapping);
    }

    public Object conventionProperty(String name) {
        return conventionAwareHelper.getConventionValue(name);
    }

    public void setConventionMapping(Map<String, ConventionValue> conventionMapping) {
        conventionAwareHelper.setConventionMapping(conventionMapping);
    }

    public Map<String, ConventionValue> getConventionMapping() {
        return conventionAwareHelper.getConventionMapping();
    }

    public ConventionAwareHelper getConventionAwareHelper() {
        return conventionAwareHelper;
    }

    public void setConventionAwareHelper(ConventionAwareHelper conventionAwareHelper) {
        this.conventionAwareHelper = conventionAwareHelper;
    }

    public Object conv(Object internalValue, String propertyName) {
        return conventionAwareHelper.getConventionValue(internalValue, propertyName);
    }

    public Conf2ScopeMappingContainer getMavenScopeMappings() {
        return (Conf2ScopeMappingContainer) conv(mavenScopeMappings, "mavenScopeMappings");
    }

    public void setMavenScopeMappings(Conf2ScopeMappingContainer mavenScopeMappings) {
        this.mavenScopeMappings = mavenScopeMappings;
    }

    public File getMavenPomDir() {
        return (File) conv(mavenPomDir, "mavenPomDir");
    }

    public void setMavenPomDir(File mavenPomDir) {
        this.mavenPomDir = mavenPomDir;
    }
}
