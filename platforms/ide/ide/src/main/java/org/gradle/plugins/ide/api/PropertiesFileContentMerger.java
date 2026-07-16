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
package org.gradle.plugins.ide.api;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.internal.PropertiesTransformer;
import org.gradle.plugins.ide.internal.IdeDeprecations;
import org.gradle.util.internal.ClosureBackedAction;

import java.util.Properties;

/**
 * Models the generation/parsing/merging capabilities.
 * Adds properties-related hooks.
 * <p>
 * For examples see docs for {@link org.gradle.plugins.ide.eclipse.model.EclipseJdt} and others.
 *
 * @deprecated Will be removed in Gradle 10.
 */
@Deprecated
public class PropertiesFileContentMerger extends FileContentMerger {

    private PropertiesTransformer transformer;

    public PropertiesFileContentMerger(PropertiesTransformer transformer) {
        this.transformer = transformer;
    }

    public PropertiesTransformer getTransformer() {
        return transformer;
    }

    public void setTransformer(PropertiesTransformer transformer) {
        this.transformer = transformer;
    }

    /**
     * Adds a closure to be called when the file has been created.
     * The {@link Properties} are passed to the closure as a parameter.
     * The closure can modify the Properties before they are written to the output file.
     * <p>
     * For examples see docs for {@link org.gradle.plugins.ide.eclipse.model.EclipseJdt} and others.
     *
     * @param closure The closure to execute when the Properties have been created.
     */
    public void withProperties(Closure closure) {
        IdeDeprecations.nagDeprecatedProperty(PropertiesFileContentMerger.class, "withProperties");
        transformer.addAction(new ClosureBackedAction<Properties>(closure, Closure.OWNER_FIRST));
    }

    /**
     * Adds an action to be called when the file has been created.
     * The {@link Properties} are passed to the action as a parameter.
     * The action can modify the Properties before they are written to the output file.
     * <p>
     * For examples see docs for {@link org.gradle.plugins.ide.eclipse.model.EclipseJdt} and others.
     *
     * @param action The action to execute when the Properties have been created.
     */
    public void withProperties(Action<Properties> action) {
        IdeDeprecations.nagDeprecatedProperty(PropertiesFileContentMerger.class, "withProperties");
        transformer.addAction(action);
    }

    // Unlike on the XML mergers, the merging hooks of this merger are removed in Gradle 10
    // together with its only access path, EclipseJdt.file. The overrides below exist to nag.

    @Override
    public void beforeMerged(Action<?> action) {
        IdeDeprecations.nagDeprecatedProperty(PropertiesFileContentMerger.class, "beforeMerged");
        super.beforeMerged(action);
    }

    @Override
    public void whenMerged(Action<?> action) {
        IdeDeprecations.nagDeprecatedProperty(PropertiesFileContentMerger.class, "whenMerged");
        super.whenMerged(action);
    }

    @Override
    public void beforeMerged(Closure closure) {
        IdeDeprecations.nagDeprecatedProperty(PropertiesFileContentMerger.class, "beforeMerged");
        super.beforeMerged(closure);
    }

    @Override
    public void whenMerged(Closure closure) {
        IdeDeprecations.nagDeprecatedProperty(PropertiesFileContentMerger.class, "whenMerged");
        super.whenMerged(closure);
    }
}
