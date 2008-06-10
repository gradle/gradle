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

package org.gradle.api.internal.dependencies

import org.apache.ivy.core.settings.IvySettings
import org.apache.ivy.plugins.resolver.ChainResolver
import org.apache.ivy.plugins.resolver.RepositoryResolver
import org.gradle.api.DependencyManager
import org.gradle.util.GradleUtil

/**
 * @author Hans Dockter
 */
class SettingsConverter {
    static final String CHAIN_RESOLVER_NAME = 'chain'
    static final String CLIENT_MODULE_CHAIN_NAME = 'clientModuleChain'
    static final String CLIENT_MODULE_NAME = 'clientModule'

    IvySettings ivySettings

    IvySettings convert(def classpathResolvers, def otherResolvers, File gradleUserHome, RepositoryResolver buildResolver,
                        Map clientModuleRegistry, Closure clientModuleChainConfigurer) {
        if (ivySettings) {return ivySettings}
        IvySettings ivySettings = new IvySettings()
        ivySettings.setVariable('ivy.cache.dir', "$gradleUserHome.canonicalPath/$DependencyManager.DEFAULT_CACHE_DIR_NAME")
        ClientModuleResolver clientModuleResolver = new ClientModuleResolver()
        clientModuleResolver.moduleRegistry = clientModuleRegistry
        clientModuleResolver.name = CLIENT_MODULE_NAME
        ChainResolver chainResolver = new ChainResolver()
        chainResolver.name = CHAIN_RESOLVER_NAME
        chainResolver.add(buildResolver)
        classpathResolvers.each {
            chainResolver.add(it)
        }
        // todo Wy has the chainResolver a higher precedence than the clientChainResolver when setting returnFirst
        chainResolver.returnFirst = true
        clientModuleResolver.mainResolver = chainResolver
        ChainResolver clientModuleChain = new ChainResolver()
        clientModuleChain.name = CLIENT_MODULE_CHAIN_NAME
        clientModuleChain.returnFirst = true
        clientModuleChain.add(clientModuleResolver)
        clientModuleChain.add(chainResolver)
        (otherResolvers + classpathResolvers + [buildResolver, clientModuleChain, clientModuleResolver, chainResolver]).each {
            ivySettings.addResolver(it)
            it.repositoryCacheManager.settings = ivySettings
        }
        ivySettings.setDefaultResolver(CLIENT_MODULE_CHAIN_NAME)
        GradleUtil.configure(clientModuleChainConfigurer, chainResolver)
        ivySettings
    }

}
