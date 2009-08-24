package org.gradle.api.internal

class AutoCreateDomainObjectContainerDelegate {
    private final Object owner;
    private final AutoCreateDomainObjectContainer delegate;

    public AutoCreateDomainObjectContainerDelegate(Object owner, AutoCreateDomainObjectContainer delegate) {
        this.delegate = delegate;
        this.owner = owner;
    }

    public Object invokeMethod(String name, Object params) {
        try {
            return delegate.invokeMethod(name, params)
        } catch (groovy.lang.MissingMethodException e) {
            // Ignore
        }

        // try the owner
        try {
            owner.invokeMethod(name, params)
        } catch (groovy.lang.MissingMethodException e) {
            // Ignore
        }

        // try the delegate again
        if (params.length == 1 && params[0] instanceof Closure) {
            delegate.add(name)
        }
        return delegate.invokeMethod(name, params);
    }

    public Object get(String name) {
        try {
            return delegate."$name"
        } catch (groovy.lang.MissingPropertyException e) {
            // Ignore
        }

        // try the owner
        try {
            return owner."$name"
        } catch (groovy.lang.MissingPropertyException e) {
            // Ignore
        }

        // try the delegate again
        delegate.add(name)
        return delegate."$name"
    }
}