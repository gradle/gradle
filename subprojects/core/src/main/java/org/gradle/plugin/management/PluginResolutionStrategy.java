package org.gradle.plugin.management;

import org.gradle.api.Action;
import org.gradle.api.Incubating;

@Incubating
public interface PluginResolutionStrategy {

    void eachPlugin(Action<? super PluginResolveDetails> rule);

}
