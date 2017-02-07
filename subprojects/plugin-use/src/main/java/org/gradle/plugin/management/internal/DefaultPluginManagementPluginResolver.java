package org.gradle.plugin.management.internal;

import org.gradle.plugin.use.PluginId;
import org.gradle.plugin.use.internal.InternalPluginRequest;
import org.gradle.plugin.use.internal.InvalidPluginRequestException;
import org.gradle.plugin.use.resolve.internal.PluginResolution;
import org.gradle.plugin.use.resolve.internal.PluginResolutionResult;
import org.gradle.plugin.use.resolve.internal.PluginResolveContext;

public class DefaultPluginManagementPluginResolver implements PluginManagementPluginResolver {

    private final InternalPluginResolutionStrategy resolutionStrategy;

    public DefaultPluginManagementPluginResolver(InternalPluginResolutionStrategy resolutionStrategy) {
        this.resolutionStrategy = resolutionStrategy;
    }

    @Override
    public void resolve(InternalPluginRequest pluginRequest, PluginResolutionResult result) throws InvalidPluginRequestException {
        final ResolvedPluginRequest request = resolutionStrategy.resolvePluginRequest(pluginRequest);

        if (null == request) {
            result.notFound("Plugin not found in PluginManagement {}", null);
        } else {
            result.found("Plugin found in PluginManagement {}", new PluginResolution() {
                @Override
                public PluginId getPluginId() {
                    return request.getPluginId();
                }

                public void execute(PluginResolveContext context) {
                    context.addLegacy(request.getPluginId(), request.getTarget());
                }
            });
        }
    }
}
