/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.plugins.ide.internal.resolver.model;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import org.gradle.plugins.ide.idea.model.Dependency;

/**
 * Key used to merge same dependency extracted from different configurations.
 */
public abstract class IdeDependencyKey<T extends IdeDependency, R> {


    public static IdeDependencyKey<IdeExtendedRepoFileDependency, Dependency> forRepoFileDependency(
            IdeExtendedRepoFileDependency dependency, DependencyBuilder<IdeExtendedRepoFileDependency, Dependency> dependencyBuilder) {
        return new RepoFileDependencyKey<Dependency>(dependency, dependencyBuilder);
    }

    public interface DependencyBuilder<T extends IdeDependency, R> {
        R buildDependency(T dependency, String scope);
    }

    public static <D> IdeDependencyKey<IdeProjectDependency, D> forProjectDependency(
            IdeProjectDependency dependency, DependencyBuilder<IdeProjectDependency, D> dependencyBuilder) {
        return new ProjectDependencyKey(dependency, dependencyBuilder);
    }

    public static <D> IdeDependencyKey<IdeLocalFileDependency, D> forLocalFileDependency(
            IdeLocalFileDependency dependency, DependencyBuilder<IdeLocalFileDependency, D> dependencyBuilder) {
        return new LocalFileDependencyKey(dependency, dependencyBuilder);
    }

    protected final T ideDependency;
    private final DependencyBuilder<T, ? extends R> dependencyBuilder;

    protected IdeDependencyKey(T ideDependency, DependencyBuilder<T, R> dependencyBuilder) {
        this.ideDependency = Preconditions.checkNotNull(ideDependency);
        this.dependencyBuilder = Preconditions.checkNotNull(dependencyBuilder);
    }

    public R buildDependency(String scope) {
        return dependencyBuilder.buildDependency(ideDependency, scope);
    }

    protected abstract boolean isSameDependency(IdeDependency otherDependency);
    protected abstract int dependencyHashCode();

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IdeDependencyKey)) {
            return false;
        }

        IdeDependencyKey that = (IdeDependencyKey) o;

        if (!isSameDependency(that.ideDependency)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return dependencyHashCode();
    }

    private static class LocalFileDependencyKey<R> extends IdeDependencyKey<IdeLocalFileDependency, R> {
        private LocalFileDependencyKey(IdeLocalFileDependency dependency, DependencyBuilder<IdeLocalFileDependency, R> dependencyBuilder) {
            super(dependency, dependencyBuilder);
        }

        @Override
        protected int dependencyHashCode() {
            return ideDependency.getFile().hashCode();
        }

        @Override
        protected boolean isSameDependency(IdeDependency otherDependency) {
            if (!(otherDependency instanceof IdeLocalFileDependency)) {
                return false;
            }
            return Objects.equal(ideDependency.getFile(), ((IdeLocalFileDependency) otherDependency).getFile());
        }

        public String toString() {
            return "LocalFileDependencyKey{" + ideDependency.getFile() + "}";
        }
    }

    private static class ProjectDependencyKey<R> extends IdeDependencyKey<IdeProjectDependency, R> {
        private ProjectDependencyKey(IdeProjectDependency dependency, DependencyBuilder<IdeProjectDependency, R> dependencyBuilder) {
            super(dependency, dependencyBuilder);
        }

        @Override
        protected int dependencyHashCode() {
            return ideDependency.getProject().hashCode();
        }

        @Override
        protected boolean isSameDependency(IdeDependency otherDependency) {
            if (!(otherDependency instanceof IdeProjectDependency)) {
                return false;
            }
            return Objects.equal(ideDependency.getProject(), ((IdeProjectDependency) otherDependency).getProject());
        }

        public String toString() {
            return "ProjectDependencyKey{" + ideDependency.getProject() + "}";
        }
    }

    private static class RepoFileDependencyKey<R> extends IdeDependencyKey<IdeExtendedRepoFileDependency, R> {
        private RepoFileDependencyKey(IdeExtendedRepoFileDependency dependency, DependencyBuilder<IdeExtendedRepoFileDependency, R> dependencyBuilder) {
            super(dependency, dependencyBuilder);
        }

        @Override
        protected int dependencyHashCode() {
            int hash = ideDependency.getFile().hashCode();
            hash = 31 * hash + (ideDependency.getId() != null ? ideDependency.getId().hashCode() : 1);
            return hash;
        }

        @Override
        protected boolean isSameDependency(IdeDependency otherDependency) {
            if (!(otherDependency instanceof IdeRepoFileDependency)) {
                return false;
            }
            IdeRepoFileDependency that = (IdeRepoFileDependency) otherDependency;
            return Objects.equal(ideDependency.getFile(), that.getFile()) && Objects.equal(ideDependency.getId(), that.getId());
        }

        public String toString() {
            return "RepoFileDependencyKey{" + ideDependency.getId() + "}";
        }
    }
}
