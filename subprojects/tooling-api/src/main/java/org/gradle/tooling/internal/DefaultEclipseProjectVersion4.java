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

package org.gradle.tooling.internal;

import org.gradle.tooling.internal.protocol.eclipse.EclipseLinkedResourceVersion1;
import org.gradle.tooling.internal.protocol.eclipse.EclipseProjectVersion3;

import java.io.File;
import java.io.Serializable;

/**
 * @author: Szczepan Faber, created at: 6/11/11
 */
//TODO SF get rid of
public class DefaultEclipseProjectVersion4 extends DefaultEclipseProject implements Serializable {

    private Iterable<? extends EclipseLinkedResourceVersion1> linkedResources;

    public DefaultEclipseProjectVersion4(String name, String path, String description, File projectDirectory, Iterable<? extends EclipseProjectVersion3> children) {
        super(name, path, description, projectDirectory, children);
    }

    public Iterable<? extends EclipseLinkedResourceVersion1> getLinkedResources() {
        return linkedResources;
    }

    public void setLinkedResources(Iterable<? extends EclipseLinkedResourceVersion1> linkedResources) {
        this.linkedResources = linkedResources;
    }
}
