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

package org.gradle.api.internal.plugins;

import org.gradle.api.Plugin;
import org.gradle.api.initialization.dsl.ScriptHandler;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.internal.initialization.ScriptHandlerFactory;
import org.gradle.api.internal.initialization.StandaloneDomainObjectContext;
import org.gradle.api.internal.resources.InsecureProtocolException;
import org.gradle.api.plugins.ObjectConfigurationAction;
import org.gradle.api.plugins.PluginAware;
import org.gradle.api.resources.TextResourceFactory;
import org.gradle.configuration.ScriptPlugin;
import org.gradle.configuration.ScriptPluginFactory;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.groovy.scripts.TextResourceScriptSource;
import org.gradle.internal.deprecation.Documentation;
import org.gradle.internal.resource.TextResource;
import org.gradle.internal.resource.TextUriResourceLoader;
import org.gradle.internal.verifier.HttpRedirectVerifier;
import org.gradle.internal.verifier.HttpRedirectVerifierFactory;
import org.gradle.util.internal.GUtil;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Set;

public class DefaultObjectConfigurationAction implements ObjectConfigurationAction {

    private final FileResolver resolver;
    private final ScriptPluginFactory configurerFactory;
    private final ScriptHandlerFactory scriptHandlerFactory;
    private final Set<Object> targets = new LinkedHashSet<Object>();
    private final Set<Runnable> actions = new LinkedHashSet<Runnable>();
    private final ClassLoaderScope classLoaderScope;
    private final TextUriResourceLoader.Factory textUriFileResourceLoaderFactory;
    private final Object defaultTarget;

    public DefaultObjectConfigurationAction(FileResolver resolver, ScriptPluginFactory configurerFactory,
                                            ScriptHandlerFactory scriptHandlerFactory, ClassLoaderScope classLoaderScope,
                                            TextUriResourceLoader.Factory textUriFileResourceLoaderFactory, Object defaultTarget) {
        this.resolver = resolver;
        this.configurerFactory = configurerFactory;
        this.scriptHandlerFactory = scriptHandlerFactory;
        this.classLoaderScope = classLoaderScope;
        this.textUriFileResourceLoaderFactory = textUriFileResourceLoaderFactory;
        this.defaultTarget = defaultTarget;
    }

    @Override
    public ObjectConfigurationAction to(Object... targets) {
        GUtil.flatten(targets, this.targets);
        return this;
    }

    @Override
    public ObjectConfigurationAction from(final Object script) {
        actions.add(new Runnable() {
            @Override
            public void run() {
                applyScript(script);
            }
        });
        return this;
    }

    @Override
    public ObjectConfigurationAction plugin(final Class<? extends Plugin> pluginClass) {
        actions.add(new Runnable() {
            @Override
            public void run() {
                applyPlugin(pluginClass);
            }
        });
        return this;
    }

    @Override
    public ObjectConfigurationAction plugin(final String pluginId) {
        actions.add(new Runnable() {
            @Override
            public void run() {
                applyType(pluginId);
            }
        });
        return this;
    }

    @Override
    public ObjectConfigurationAction type(final Class<?> pluginClass) {
        actions.add(new Runnable() {
            @Override
            public void run() {
                applyType(pluginClass);
            }
        });
        return this;
    }

    private HttpRedirectVerifier createHttpRedirectVerifier(URI scriptUri) {
        return HttpRedirectVerifierFactory.create(
            scriptUri,
            false,
            () -> {
                throw new InsecureProtocolException(
                    String.format("Applying script plugins from insecure URIs, without explicit opt-in, is unsupported. The provided URI '%s' uses an insecure protocol (HTTP). ", scriptUri),
                    String.format("Use '%s' instead or try 'apply from: resources.text.fromInsecureUri(\"%s\")'. ", GUtil.toSecureUrl(scriptUri), scriptUri),
                    Documentation.dslReference(TextResourceFactory.class, "fromInsecureUri(java.lang.Object)").getConsultDocumentationMessage()
                );
            },
            redirect -> {
                throw new InsecureProtocolException(
                    String.format("Applying script plugins from an insecure redirect, without explicit opt-in, is unsupported. '%s' redirects to insecure '%s'. ", scriptUri, redirect),
                    "Switch to HTTPS or use TextResourceFactory.fromInsecureUri(Object).",
                    Documentation.dslReference(TextResourceFactory.class, "fromInsecureUri(java.lang.Object)").getConsultDocumentationMessage()
                );
            }
        );
    }

    private void applyScript(Object script) {
        URI scriptUri = resolver.resolveUri(script);
        TextResource resource;
        if (script instanceof TextResource) {
            resource = (TextResource) script;
        } else {
            HttpRedirectVerifier redirectVerifier = createHttpRedirectVerifier(scriptUri);
            resource = textUriFileResourceLoaderFactory.create(redirectVerifier).loadUri("script", scriptUri);
        }
        ScriptSource scriptSource = new TextResourceScriptSource(resource);
        ClassLoaderScope classLoaderScopeChild = classLoaderScope.createChild("script-" + scriptUri, null);
        ScriptHandler scriptHandler = scriptHandlerFactory.create(scriptSource, classLoaderScopeChild, StandaloneDomainObjectContext.forScript(scriptSource));
        ScriptPlugin configurer = configurerFactory.create(scriptSource, scriptHandler, classLoaderScopeChild, classLoaderScope, false);
        for (Object target : targets) {
            configurer.apply(target);
        }
    }

    private void applyPlugin(Class<? extends Plugin> pluginClass) {
        applyType(pluginClass);
    }

    private void applyType(String pluginId) {
        for (Object target : targets) {
            if (target instanceof PluginAware) {
                ((PluginAware) target).getPluginManager().apply(pluginId);
            } else {
                throw new UnsupportedOperationException(String.format("Cannot apply plugin with id '%s' to '%s' (class: %s) as it does not implement PluginAware", pluginId, target.toString(), target.getClass().getName()));
            }
        }
    }

    private void applyType(Class<?> pluginClass) {
        for (Object target : targets) {
            if (target instanceof PluginAware) {
                ((PluginAware) target).getPluginManager().apply(pluginClass);
            } else {
                throw new UnsupportedOperationException(String.format("Cannot apply plugin of class '%s' to '%s' (class: %s) as it does not implement PluginAware", pluginClass.getName(), target.toString(), target.getClass().getName()));
            }
        }
    }

    public void execute() {
        if (targets.isEmpty()) {
            to(defaultTarget);
        }

        for (Runnable action : actions) {
            action.run();
        }
    }
}
