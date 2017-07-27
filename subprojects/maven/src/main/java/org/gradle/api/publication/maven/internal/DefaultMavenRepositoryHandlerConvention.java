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
package org.gradle.api.publication.maven.internal;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.artifacts.maven.GroovyMavenDeployer;
import org.gradle.api.artifacts.maven.MavenResolver;
import org.gradle.api.internal.ConfigureByMapAction;
import org.gradle.api.internal.artifacts.dsl.DefaultRepositoryHandler;
import org.gradle.api.plugins.MavenRepositoryHandlerConvention;

import java.util.Map;

import static org.gradle.internal.Actions.composite;
import static org.gradle.util.ConfigureUtil.configureUsing;

public class DefaultMavenRepositoryHandlerConvention implements MavenRepositoryHandlerConvention {
    private final DefaultRepositoryHandler container;
    private final DeployerFactory deployerFactory;

    public DefaultMavenRepositoryHandlerConvention(DefaultRepositoryHandler container, DeployerFactory deployerFactory) {
        this.container = container;
        this.deployerFactory = deployerFactory;
    }

    public GroovyMavenDeployer mavenDeployer() {
        return container.addRepository(createMavenDeployer(), DEFAULT_MAVEN_DEPLOYER_NAME);
    }

    @Override
    public GroovyMavenDeployer mavenDeployer(Action<? super GroovyMavenDeployer> configureAction) {
        return container.addRepository(createMavenDeployer(), DEFAULT_MAVEN_DEPLOYER_NAME, configureAction);
    }

    public GroovyMavenDeployer mavenDeployer(Closure configureClosure) {
        return mavenDeployer(configureUsing(configureClosure));
    }

    public GroovyMavenDeployer mavenDeployer(Map<String, ?> args) {
        return mavenDeployer(configureByMapActionFor(args));
    }

    public GroovyMavenDeployer mavenDeployer(Map<String, ?> args, Closure configureClosure) {
        return mavenDeployer(args, configureUsing(configureClosure));
    }

    public GroovyMavenDeployer mavenDeployer(Map<String, ?> args, Action<? super GroovyMavenDeployer> configureAction) {
        //noinspection unchecked
        return mavenDeployer(composite(configureByMapActionFor(args), configureAction));
    }

    private GroovyMavenDeployer createMavenDeployer() {
        return deployerFactory.createMavenDeployer();
    }

    public MavenResolver mavenInstaller() {
        return container.addRepository(createMavenInstaller(), DEFAULT_MAVEN_INSTALLER_NAME);
    }

    public MavenResolver mavenInstaller(Closure configureClosure) {
        return mavenInstaller(configureUsing(configureClosure));
    }

    @Override
    public MavenResolver mavenInstaller(Action<? super MavenResolver> configureAction) {
        return container.addRepository(createMavenInstaller(), DEFAULT_MAVEN_INSTALLER_NAME, configureAction);
    }

    public MavenResolver mavenInstaller(Map<String, ?> args) {
        return mavenInstaller(configureByMapActionFor(args));
    }

    public MavenResolver mavenInstaller(Map<String, ?> args, Closure configureClosure) {
        return mavenInstaller(args, configureUsing(configureClosure));
    }

    @Override
    public MavenResolver mavenInstaller(Map<String, ?> args, Action<? super MavenResolver> configureAction) {
        //noinspection unchecked
        return mavenInstaller(composite(configureByMapActionFor(args), configureAction));
    }

    private MavenResolver createMavenInstaller() {
        return deployerFactory.createMavenInstaller();
    }

    private <T> ConfigureByMapAction<T> configureByMapActionFor(Map<String, ?> args) {
        return new ConfigureByMapAction<T>(args);
    }
}
