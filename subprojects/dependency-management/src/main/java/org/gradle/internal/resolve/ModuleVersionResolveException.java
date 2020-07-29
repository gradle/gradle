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
package org.gradle.internal.resolve;

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.internal.artifacts.dependencies.DefaultImmutableVersionConstraint;
import org.gradle.internal.Factory;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector;
import org.gradle.internal.exceptions.Contextual;
import org.gradle.internal.exceptions.DefaultMultiCauseExceptionNoStackTrace;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Formatter;
import java.util.List;

@Contextual
public class ModuleVersionResolveException extends DefaultMultiCauseExceptionNoStackTrace {
    private final List<List<? extends ComponentIdentifier>> paths = new ArrayList<>();
    private final ComponentSelector selector;

    public ModuleVersionResolveException(ComponentSelector selector, Factory<String> message, Throwable cause) {
        super(message, cause);
        this.selector = selector;
    }

    public ModuleVersionResolveException(ComponentSelector selector, Factory<String> message) {
        super(message);
        this.selector = selector;
    }

    public ModuleVersionResolveException(ComponentSelector selector, Throwable cause) {
        this(selector, format("Could not resolve %s.", selector));
        initCause(cause);
    }

    public ModuleVersionResolveException(ComponentSelector selector, Iterable<? extends Throwable> causes) {
        this(selector, format("Could not resolve %s.", selector));
        initCauses(causes);
    }

    public ModuleVersionResolveException(ModuleVersionSelector selector, Factory<String> message) {
        this(DefaultModuleComponentSelector.newSelector(selector), message);
    }

    public ModuleVersionResolveException(ModuleVersionIdentifier id, Factory<String> message) {
        this(DefaultModuleComponentSelector.newSelector(id.getModule(), DefaultImmutableVersionConstraint.of(id.getVersion())), message);
    }

    public ModuleVersionResolveException(ModuleComponentIdentifier id, Factory<String> messageFormat) {
        this(DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId(id.getGroup(), id.getModule()), DefaultImmutableVersionConstraint.of(id.getVersion())), messageFormat);
    }

    public ModuleVersionResolveException(ModuleComponentIdentifier id, Throwable cause) {
        this(DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId(id.getGroup(), id.getModule()), DefaultImmutableVersionConstraint.of(id.getVersion())), Collections.singletonList(cause));
    }

    public ModuleVersionResolveException(ModuleComponentIdentifier id, Iterable<? extends Throwable> causes) {
        this(DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId(id.getGroup(), id.getModule()), DefaultImmutableVersionConstraint.of(id.getVersion())), causes);
    }

    public ModuleVersionResolveException(ModuleVersionSelector selector, Throwable cause) {
        this(DefaultModuleComponentSelector.newSelector(selector), cause);
    }

    public ModuleVersionResolveException(ModuleVersionSelector selector, Iterable<? extends Throwable> causes) {
        this(DefaultModuleComponentSelector.newSelector(selector), causes);
    }

    /**
     * Returns the selector that could not be resolved.
     */
    public ComponentSelector getSelector() {
        return selector;
    }

    protected static Factory<String> format(String messageFormat, ComponentSelector selector) {
        return () -> String.format(messageFormat, selector.getDisplayName());
    }

    /**
     * Creates a copy of this exception, with the given incoming paths.
     */
    public ModuleVersionResolveException withIncomingPaths(Collection<? extends List<? extends ComponentIdentifier>> paths) {
        ModuleVersionResolveException copy = createCopy();
        copy.paths.addAll(paths);
        copy.initCauses(getCauses());
        copy.setStackTrace(getStackTrace());
        return copy;
    }

    @Override
    public String getMessage() {
        if (paths.isEmpty()) {
            return super.getMessage();
        }
        Formatter formatter = new Formatter();
        formatter.format("%s%nRequired by:", super.getMessage());
        for (List<? extends ComponentIdentifier> path : paths) {
            formatter.format("%n    %s", toString(path.get(0)));
            for (int i = 1; i < path.size(); i++) {
                formatter.format(" > %s", toString(path.get(i)));
            }
        }
        return formatter.toString();
    }

    private String toString(ComponentIdentifier identifier) {
        return identifier.getDisplayName();
    }

    protected ModuleVersionResolveException createCopy() {
        try {
            String message = getMessage();
            return getClass().getConstructor(ComponentSelector.class, Factory.class).newInstance(selector, (Factory<String>) () -> message);
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }
}
