package org.gradle.api.internal.artifacts.repositories;

import org.apache.ivy.plugins.resolver.RepositoryResolver;

public class CommonsHttpClientResolver extends RepositoryResolver {
    public CommonsHttpClientResolver(String username, String password) {
        setRepository(new CommonsHttpClientBackedRepository(username, password));
    }
}
