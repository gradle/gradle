package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser;

import org.apache.ivy.core.settings.IvyVariableContainer;
import org.apache.ivy.core.settings.IvyVariableContainerImpl;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class IvyVariableContainerWithFallback implements IvyVariableContainer {
    private final IvyVariableContainerImpl delegate;
    private final String fallback;
    private final Set<String> enableFallbackFor; // null to enable fallback for all variables

    public IvyVariableContainerWithFallback(Map<String, String> properties, String fallback) {
        this(properties, fallback, null);
    }

    public IvyVariableContainerWithFallback(Map<String, String> properties, String fallback, Set<String> enableFallbackFor) {
        this(new IvyVariableContainerImpl(properties), fallback, enableFallbackFor);
    }

    private IvyVariableContainerWithFallback(IvyVariableContainerImpl delegate, String fallback, Set<String> enableFallbackFor) {
         this.delegate =  delegate;
         this.fallback = fallback;
         this.enableFallbackFor = enableFallbackFor;
    }

    @Override
    public void setVariable(String varName, String value, boolean overwrite) {
        delegate.setVariable(varName, value, overwrite);
    }

    @Override
    public String getVariable(String name) {
        String result = delegate.getVariable(name);
        if(result == null && (enableFallbackFor == null || enableFallbackFor.contains(name))) {
            result = fallback;
        }
        return result;
    }

    @Override
    public void setEnvironmentPrefix(String prefix) {
        delegate.setEnvironmentPrefix(prefix);
    }

    @Override
    public IvyVariableContainerWithFallback clone() {
        IvyVariableContainerImpl delegateClone = (IvyVariableContainerImpl) delegate.clone();
        Set<String> enableFallbackForClone = enableFallbackFor;
        if(enableFallbackForClone != null) {
            enableFallbackForClone = new HashSet<String>(enableFallbackForClone);
        }
        return new IvyVariableContainerWithFallback(delegateClone, fallback, enableFallbackForClone);
    }
}
