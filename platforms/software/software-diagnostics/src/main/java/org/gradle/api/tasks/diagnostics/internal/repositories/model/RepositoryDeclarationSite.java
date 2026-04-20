/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */
package org.gradle.api.tasks.diagnostics.internal.repositories.model;

import org.gradle.util.Path;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

@NullMarked
public final class RepositoryDeclarationSite {
    public enum Scope { SETTINGS, PROJECT }

    private final Scope scope;
    private final @Nullable Path projectPath;
    private final String block;

    public RepositoryDeclarationSite(Scope scope, @Nullable Path projectPath, String block) {
        this.scope = Objects.requireNonNull(scope);
        this.projectPath = projectPath;
        this.block = Objects.requireNonNull(block);
        if (scope == Scope.PROJECT && projectPath == null) {
            throw new IllegalArgumentException("PROJECT scope requires a non-null projectPath");
        }
        if (scope == Scope.SETTINGS && projectPath != null) {
            throw new IllegalArgumentException("SETTINGS scope requires a null projectPath");
        }
    }

    public Scope getScope() {
        return scope;
    }

    public @Nullable Path getProjectPath() {
        return projectPath;
    }

    public String getBlock() {
        return block;
    }

    public String render() {
        if (scope == Scope.SETTINGS) {
            return "settings > " + block;
        }
        return "project '" + projectPath + "' > " + block;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RepositoryDeclarationSite)) return false;
        RepositoryDeclarationSite that = (RepositoryDeclarationSite) o;
        return scope == that.scope
            && Objects.equals(projectPath, that.projectPath)
            && block.equals(that.block);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scope, projectPath, block);
    }
}
