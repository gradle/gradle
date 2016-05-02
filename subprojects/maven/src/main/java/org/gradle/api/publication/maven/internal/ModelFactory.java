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
package org.gradle.api.publication.maven.internal;

import groovy.util.FactoryBuilderSupport;
import org.apache.maven.model.Model;
import org.sonatype.maven.polyglot.groovy.builder.factory.NamedFactory;

import java.util.Map;

/**
 * This is a slightly modified version as shipped with polyglot Maven.
 */
public class ModelFactory extends NamedFactory {
    private Model model;

    public ModelFactory(Model model) {
        super("project");
        this.model = model;
    }

    public Object newInstance(FactoryBuilderSupport builder, Object name, Object value, Map attrs) throws InstantiationException, IllegalAccessException {
        return model;
    }

    @Override
    public void onNodeCompleted(FactoryBuilderSupport builder, Object parent, Object node) {
        Model model = (Model)node;
    }
}