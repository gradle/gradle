package org.gradle.plugin.management.internal;

import org.gradle.api.Action;
import org.gradle.api.Nullable;
import org.gradle.plugin.management.PluginRequest;
import org.gradle.plugin.management.PluginResolveDetails;

import java.util.LinkedHashSet;
import java.util.Set;

public class PluginResolutions {

    private final Set<Action<? super PluginResolveDetails>> resolutionDetails = new LinkedHashSet<Action<? super PluginResolveDetails>>();

    public void add(Action<? super PluginResolveDetails> rule) {
        resolutionDetails.add(rule);
    }

    @Nullable
    public ResolvedPluginRequest resolveRequest(PluginRequest pluginRequest) {
        for (Action<? super PluginResolveDetails> resolutionDetail : resolutionDetails) {
            DefaultPluginResolveDetails defaultPluginResolveDetails = new DefaultPluginResolveDetails(pluginRequest);
            resolutionDetail.execute(defaultPluginResolveDetails);

            if (null == defaultPluginResolveDetails.getResolvedPluginRequest()) {
                return defaultPluginResolveDetails.getResolvedPluginRequest();
            }
        }

        return null;
    }

}
