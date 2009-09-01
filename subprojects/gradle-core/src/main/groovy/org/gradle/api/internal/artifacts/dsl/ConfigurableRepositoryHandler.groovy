package org.gradle.api.internal.artifacts.dsl

import org.gradle.api.internal.artifacts.ivyservice.ResolverFactory

/**
 * This is here because DefaultRepositoryHandler.methodMissing() and propertyMissing() aren't being called by
 * Groovy when the handler is the delegate of a closure. Need to figure out why....
 */
class ConfigurableRepositoryHandler extends DefaultRepositoryHandler {
    def ConfigurableRepositoryHandler(ResolverFactory resolverFactory) {
        super(resolverFactory);
    }
}