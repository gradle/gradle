/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.plugins.signing.signatory.gnupg;

import groovy.lang.Closure;
import org.gradle.api.Project;
import org.gradle.plugins.signing.SigningExtension;
import org.gradle.plugins.signing.signatory.SignatoryProvider;
import org.gradle.util.ConfigureUtil;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A {@link SignatoryProvider} of {@link GnupgSignatory} instances.
 */
public class GnupgSignatoryProvider implements SignatoryProvider<GnupgSignatory> {

    private final GnupgSignatoryFactory factory = new GnupgSignatoryFactory();
    private final Map<String, GnupgSignatory> signatories = new LinkedHashMap<String, GnupgSignatory>();

    @Override
    public void configure(SigningExtension settings, Closure closure) {
        ConfigureUtil.configure(closure, new Dsl(settings.getProject(), signatories, factory));
    }

    @Override
    public GnupgSignatory getDefaultSignatory(Project project) {
        return factory.createSignatory(project);
    }

    @Override
    public GnupgSignatory getSignatory(String name) {
        return signatories.get(name);
    }

    @SuppressWarnings("unused") // invoked by Groovy
    public GnupgSignatory propertyMissing(String signatoryName) {
        return getSignatory(signatoryName);
    }

}
