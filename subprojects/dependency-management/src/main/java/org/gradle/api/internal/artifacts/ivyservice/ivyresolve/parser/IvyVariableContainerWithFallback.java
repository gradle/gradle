package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser;

import org.apache.ivy.core.settings.IvyVariableContainer;
import org.apache.ivy.core.settings.IvyVariableContainerImpl;

import java.util.Map;

public class IvyVariableContainerWithFallback implements IvyVariableContainer {
    private final IvyVariableContainerImpl delegate;
    private final String fallback;

    public IvyVariableContainerWithFallback(Map<String, String> properties, String fallback) {
        this(new IvyVariableContainerImpl(properties), fallback);
    }

    private IvyVariableContainerWithFallback(IvyVariableContainerImpl delegate, String fallback) {
        this.delegate = delegate;
        this.fallback = fallback;
    }

    @Override
    public void setVariable(String varName, String value, boolean overwrite) {
        delegate.setVariable(varName, value, overwrite);
    }

    @Override
    public String getVariable(String name) {
        String result = delegate.getVariable(name);
        if(result == null) {
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
        return new IvyVariableContainerWithFallback(delegateClone,fallback);
    }
}
