/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.tasks;

import org.gradle.api.Namer;
import org.gradle.api.internal.AbstractNamedDomainObjectContainer;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.internal.reflect.Instantiator;

public class DefaultSourceSetContainer extends AbstractNamedDomainObjectContainer<SourceSet> implements SourceSetContainer {
    private final FileResolver fileResolver;
    private final TaskResolver taskResolver;
    private final Instantiator instantiator;

    public DefaultSourceSetContainer(FileResolver fileResolver, TaskResolver taskResolver, Instantiator classGenerator) {
        super(SourceSet.class, classGenerator, new Namer<SourceSet>() { public String determineName(SourceSet ss) { return ss.getName(); }});
        this.fileResolver = fileResolver;
        this.taskResolver = taskResolver;
        this.instantiator = classGenerator;
    }

    @Override
    protected SourceSet doCreate(String name) {
        DefaultSourceSet sourceSet = instantiator.newInstance(DefaultSourceSet.class, name, fileResolver);
        sourceSet.setClasses(instantiator.newInstance(DefaultSourceSetOutput.class, sourceSet.getDisplayName(), fileResolver, taskResolver));

        return sourceSet;
    }
}