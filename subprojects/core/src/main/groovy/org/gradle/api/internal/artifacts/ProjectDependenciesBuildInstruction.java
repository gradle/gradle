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
package org.gradle.api.internal.artifacts;

/**
 * @author Hans Dockter
 */
public class ProjectDependenciesBuildInstruction {
    private final boolean rebuild;

    public ProjectDependenciesBuildInstruction(boolean rebuild) {
        this.rebuild = rebuild;
    }

    public boolean isRebuild() {
        return rebuild;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ProjectDependenciesBuildInstruction that = (ProjectDependenciesBuildInstruction) o;

        return rebuild == that.rebuild;
    }

    @Override
    public int hashCode() {
        return rebuild ? 1 : 0;
    }
}
