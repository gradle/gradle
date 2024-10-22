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
package org.gradle.security.internal.gnupg;

import org.gradle.api.Project;
import org.gradle.security.internal.BaseSignatoryProvider;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A {@link BaseSignatoryProvider} of {@link GnupgSignatory} instances.
 *
 * @since 4.5
 */
public class BaseGnupgSignatoryProvider implements BaseSignatoryProvider<GnupgSignatory> {

    private final GnupgSignatoryFactory factory = new GnupgSignatoryFactory();
    private final Map<String, GnupgSignatory> signatories = new LinkedHashMap<String, GnupgSignatory>();


    protected void addSignatory(Project project, String name) {
        signatories.put(name, factory.createSignatory(project, name, name));
    }

    @Override
    public GnupgSignatory getDefaultSignatory(Project project) {
        return factory.createSignatory(project);
    }

    @Override
    public GnupgSignatory getSignatory(String name) {
        return signatories.get(name);
    }


}
