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

package org.gradle.tooling.internal.provider;

import org.gradle.api.internal.GradleInternal;
import org.gradle.tooling.internal.protocol.InternalIdeaProject;
import org.gradle.tooling.internal.protocol.ProjectVersion3;
import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.elements.HierarchicalElement;

import java.io.File;

/**
 * @author: Szczepan Faber, created at: 7/23/11
 */
public class IdeaModelBuilder implements BuildsModel {
    public boolean canBuild(Class type) {
        return type == InternalIdeaProject.class;
    }

    public ProjectVersion3 buildAll(GradleInternal gradle) {
        return new InternalIdeaProject() {
            public String getId() {
                return null;  //To change body of implemented methods use File | Settings | File Templates.
            }

            public HierarchicalElement getParent() {
                return null;  //To change body of implemented methods use File | Settings | File Templates.
            }

            public DomainObjectSet<? extends HierarchicalElement> getChildren() {
                return null;  //To change body of implemented methods use File | Settings | File Templates.
            }

            public String getPath() {
                return null;  //To change body of implemented methods use File | Settings | File Templates.
            }

            public String getName() {
                return null;  //To change body of implemented methods use File | Settings | File Templates.
            }

            public String getDescription() {
                return null;  //To change body of implemented methods use File | Settings | File Templates.
            }

            public File getProjectDirectory() {
                return null;  //To change body of implemented methods use File | Settings | File Templates.
            }

            public String getFoo() {
                return "foo";
            }
        };
    }
}
