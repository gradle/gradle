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
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.exceptions.DefaultMultiCauseException;
import org.gradle.internal.exceptions.Contextual;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Formatter;
import java.util.List;

@Contextual
public class ModuleVersionResolveException extends DefaultMultiCauseException {
    private final List<List<ModuleVersionIdentifier>> paths = new ArrayList<List<ModuleVersionIdentifier>>();
    private final String messageFormat;
    private final ModuleVersionSelector selector;

    public ModuleVersionResolveException(ModuleVersionSelector selector, String messageFormat) {
        super(format(messageFormat, selector));
        this.selector = selector;
        this.messageFormat = messageFormat;
    }

    public ModuleVersionResolveException(ModuleVersionIdentifier id, String messageFormat) {
        this(DefaultModuleVersionSelector.newSelector(id.getGroup(), id.getName(), id.getVersion()), messageFormat);
    }

    public ModuleVersionResolveException(ModuleComponentIdentifier id, String messageFormat) {
        this(DefaultModuleVersionSelector.newSelector(id.getGroup(), id.getModule(), id.getVersion()), messageFormat);
    }

    public ModuleVersionResolveException(ModuleComponentIdentifier id, Iterable<? extends Throwable> causes) {
        this(DefaultModuleVersionSelector.newSelector(id.getGroup(), id.getModule(), id.getVersion()), causes);
    }

    public ModuleVersionResolveException(ModuleVersionSelector selector, Throwable cause) {
        this(selector, "Could not resolve %s.");
        initCause(cause);
    }

    public ModuleVersionResolveException(ModuleVersionSelector selector, Iterable<? extends Throwable> causes) {
        this(selector, "Could not resolve %s.");
        initCauses(causes);
    }

    /**
     * Returns the selector that could not be resolved.
     */
    public ModuleVersionSelector getSelector() {
        return selector;
    }

    private static String format(String messageFormat, ModuleVersionSelector id) {
        return format(messageFormat, id.getGroup(), id.getName(), id.getVersion());
    }

    private static String format(String messageFormat, String group, String name, String version) {
        return String.format(messageFormat, String.format("%s:%s:%s", group, name, version));
    }

    /**
     * Creates a copy of this exception, with the given incoming paths.
     */
    public ModuleVersionResolveException withIncomingPaths(Collection<? extends List<ModuleVersionIdentifier>> paths) {
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
        for (List<ModuleVersionIdentifier> path : paths) {
            formatter.format("%n    %s", toString(path.get(0)));
            for (int i = 1; i < path.size(); i++) {
                formatter.format(" > %s", toString(path.get(i)));
            }
        }
        return formatter.toString();
    }

    private String toString(ModuleVersionIdentifier identifier) {
        return identifier.toString();
    }

    protected ModuleVersionResolveException createCopy() {
        try {
            return getClass().getConstructor(ModuleVersionSelector.class, String.class).newInstance(selector, messageFormat);
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

}
