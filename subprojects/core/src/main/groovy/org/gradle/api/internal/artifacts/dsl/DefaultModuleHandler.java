package org.gradle.api.internal.artifacts.dsl;

import org.gradle.api.Action;
import org.gradle.api.artifacts.ModuleDetails;
import org.gradle.api.artifacts.ModuleMetadataProcessor;
import org.gradle.api.artifacts.dsl.ModuleHandler;
import org.gradle.listener.ActionBroadcast;

public class DefaultModuleHandler implements ModuleHandler, ModuleMetadataProcessor {
    private final ActionBroadcast<ModuleDetails> moduleRules = new ActionBroadcast<ModuleDetails>();

    public void eachModule(Action<? super ModuleDetails> action) {
        moduleRules.add(action);
    }

    public void process(ModuleDetails details) {
        moduleRules.execute(details);
    }
}
