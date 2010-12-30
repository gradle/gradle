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
package org.gradle.api.internal.artifacts.dsl;

import groovy.lang.Closure;
import org.apache.ivy.plugins.resolver.AbstractResolver;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.apache.ivy.plugins.resolver.FileSystemResolver;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.maven.GroovyMavenDeployer;
import org.gradle.api.artifacts.maven.MavenResolver;
import org.gradle.api.internal.ClassGenerator;
import org.gradle.api.internal.artifacts.DefaultResolverContainer;
import org.gradle.api.internal.artifacts.ivyservice.ResolverFactory;
import org.gradle.util.GUtil;
import org.gradle.util.HashUtil;
import org.gradle.util.WrapUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Hans Dockter
 */
public class DefaultRepositoryHandler extends DefaultResolverContainer implements RepositoryHandler {
    public DefaultRepositoryHandler(ResolverFactory resolverFactory, ClassGenerator classGenerator) {
        super(resolverFactory, classGenerator);
    }

    public FileSystemResolver flatDir(Map args) {
        Object[] rootDirPaths = getFlatDirRootDirs(args);
        File[] rootDirs = new File[rootDirPaths.length];
        for (int i = 0; i < rootDirPaths.length; i++) {
            Object rootDirPath = rootDirPaths[i];
            rootDirs[i] = new File(rootDirPath.toString());
        }
        FileSystemResolver resolver = getResolverFactory().createFlatDirResolver(
                getNameFromMap(args, HashUtil.createHash(GUtil.join(rootDirPaths, ""))),
                rootDirs);
        return (FileSystemResolver) add(resolver);
    }

    private String getNameFromMap(Map args, String defaultName) {
        Object name = args.get("name");
        return name != null ? name.toString() : defaultName;
    }

    private Object[] getFlatDirRootDirs(Map args) {
        List dirs = createStringifiedListFromMapArg(args, "dirs");
        if (dirs == null) {
            throw new InvalidUserDataException("You must specify dirs for the flat dir repository.");
        }
        return dirs.toArray();
    }

    private List<String> createStringifiedListFromMapArg(Map args, String argName) {
        Object dirs = args.get(argName);
        if (dirs == null) {
            return null;
        }
        Iterable<Object> iterable;
        if (dirs instanceof Iterable) {
            iterable = (Iterable<Object>) dirs;
        } else {
            iterable = WrapUtil.toSet(dirs);
        }
        List<String> list = new ArrayList<String>();
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
        return add(getResolverFactory().createMavenRepoResolver(
                getNameFromMap(args, DEFAULT_MAVEN_CENTRAL_REPO_NAME),
                MAVEN_CENTRAL_URL,
                urls == null ? new String[0] : urls.toArray(new String[urls.size()])));
    }

    public DependencyResolver mavenRepo(Map args) {
        return mavenRepo(args, null);
    }

    public DependencyResolver mavenRepo(Map args, Closure configClosure) {
        List<String> urls = createStringifiedListFromMapArg(args, "urls");
        if (urls == null) {
            throw new InvalidUserDataException("You must specify a urls for a Maven repo.");
        }
        List<String> extraUrls = urls.subList(1, urls.size());
        AbstractResolver resolver = getResolverFactory().createMavenRepoResolver(
                getNameFromMap(args, urls.get(0)),
                urls.get(0),
                urls.size() == 1 ? new String[0] : extraUrls.toArray(new String[extraUrls.size()]));
        return add(resolver, configClosure);
    }

    public GroovyMavenDeployer mavenDeployer(Map args) {
        GroovyMavenDeployer mavenDeployer = createMavenDeployer(args);
        return (GroovyMavenDeployer) add(mavenDeployer);
    }

    private GroovyMavenDeployer createMavenDeployer(Map args) {
        GroovyMavenDeployer mavenDeployer = createMavenDeployer("dummyName");
        String defaultName = RepositoryHandler.DEFAULT_MAVEN_DEPLOYER_NAME + "-" + System.identityHashCode(
                mavenDeployer);
        mavenDeployer.setName(getNameFromMap(args, defaultName));
        return mavenDeployer;
    }

    public GroovyMavenDeployer mavenDeployer() {
        return mavenDeployer(Collections.emptyMap());
    }

    public GroovyMavenDeployer mavenDeployer(Closure configureClosure) {
        return mavenDeployer(Collections.emptyMap(), configureClosure);
    }

    public GroovyMavenDeployer mavenDeployer(Map args, Closure configureClosure) {
        GroovyMavenDeployer mavenDeployer = createMavenDeployer(args);
        return (GroovyMavenDeployer) add(mavenDeployer, configureClosure);
    }

    public MavenResolver mavenInstaller() {
        return mavenInstaller(Collections.emptyMap());
    }

    public MavenResolver mavenInstaller(Closure configureClosure) {
        return mavenInstaller(Collections.emptyMap(), configureClosure);
    }

    public MavenResolver mavenInstaller(Map args) {
        MavenResolver mavenInstaller = createMavenInstaller(args);
        return (MavenResolver) add(mavenInstaller);
    }

    public MavenResolver mavenInstaller(Map args, Closure configureClosure) {
        MavenResolver mavenInstaller = createMavenInstaller(args);
        return (MavenResolver) add(mavenInstaller, configureClosure);
    }

    private MavenResolver createMavenInstaller(Map args) {
        MavenResolver mavenInstaller = createMavenInstaller("dummyName");
        String defaultName = RepositoryHandler.DEFAULT_MAVEN_INSTALLER_NAME + "-" + System.identityHashCode(
                mavenInstaller);
        mavenInstaller.setName(getNameFromMap(args, defaultName));
        return mavenInstaller;
    }
}
