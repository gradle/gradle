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
package org.gradle.api.internal.artifacts.ivyservice;

import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.internal.AbstractMultiCauseException;
import org.gradle.api.internal.Contextual;
import org.gradle.internal.UncheckedException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Formatter;
import java.util.List;

@Contextual
public class ModuleVersionResolveException extends AbstractMultiCauseException {
    private final List<List<ModuleRevisionId>> paths = new ArrayList<List<ModuleRevisionId>>();

    public ModuleVersionResolveException(String message) {
        super(message);
    }

    public ModuleVersionResolveException(String message, ModuleVersionSelector selector, Throwable cause) {
        super(format(message, selector), cause);
    }

    public ModuleVersionResolveException(ModuleRevisionId id, Throwable cause) {
        super(format("Could not resolve ", id), cause);
    }

    public ModuleVersionResolveException(ModuleRevisionId id, Iterable<? extends Throwable> causes) {
        super(format("Could not resolve ", id), causes);
    }

    public ModuleVersionResolveException(String message, Throwable cause) {
        super(message, cause);
    }

    private static String format(String message, ModuleRevisionId id) {
        return format(message, id.getOrganisation(), id.getName(), id.getRevision());
    }

    private static String format(String message, ModuleVersionSelector id) {
        return format(message, id.getGroup(), id.getName(), id.getVersion());
    }

    private static String format(String message, String group, String name, String version) {
        return String.format(message + "%s:%s:%s.", group, name, version);
    }

    /**
     * Creates a copy of this exception, with the given incoming paths.
     */
    public ModuleVersionResolveException withIncomingPaths(Collection<? extends List<ModuleRevisionId>> paths) {
        ModuleVersionResolveException copy = createCopy(super.getMessage());
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
        for (List<ModuleRevisionId> path : paths) {
            formatter.format("%n    %s", toString(path.get(0)));
            for (int i = 1; i < path.size(); i++) {
                formatter.format(" > %s", toString(path.get(i)));
            }
        }
        return formatter.toString();
    }

    private String toString(ModuleRevisionId moduleRevisionId) {
        return String.format("%s:%s:%s", moduleRevisionId.getOrganisation(), moduleRevisionId.getName(), moduleRevisionId.getRevision());
    }

    protected ModuleVersionResolveException createCopy(String message) {
        try {
            return getClass().getConstructor(String.class).newInstance(message);
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

}
