package org.gradle.util;

import groovy.lang.Closure;
import org.codehaus.groovy.runtime.InvokerInvocationException;
import org.gradle.api.GradleException;
import org.gradle.api.GradleScriptException;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.groovy.scripts.ScriptWithSource;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * <p>Manages a set of listeners of type T. Provides an implementation of T which can be used to broadcast to all
 * registered listeners.</p>
 *
 * @param <T> The listener type.
 */
public class ListenerBroadcast<T> {
    private final T source;
    private final Class<T> type;
    private final Map<Object, InvocationHandler> handlers = new LinkedHashMap<Object, InvocationHandler>();

    public ListenerBroadcast(Class<T> type) {
        this.type = type;
        source = type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                new BroadcastInvocationHandler()));
    }

    /**
     * Returns the broadcaster. Any method call on this object is broadcast to all listeners.
     *
     * @return The broadcaster.
     */
    public T getSource() {
        return source;
    }

    /**
     * Adds a listener.
     *
     * @param listener The listener.
     */
    public void add(T listener) {
        handlers.put(listener, new ListenerInvocationHandler(listener));
    }

    /**
     * Adds a closure to be notified when the given method is called.
     */
    public void add(String methodName, Closure closure) {
        handlers.put(closure, new ClosureInvocationHandler(methodName, closure));
    }

    public void remove(T listener) {
        handlers.remove(listener);
    }

    private String getErrorMessage() {
        String typeDescription = type.getSimpleName().replaceAll("(\\p{Upper})", " $1").trim().toLowerCase();
        return String.format("Failed to notify %s.", typeDescription);
    }

    private class BroadcastInvocationHandler implements InvocationHandler {
        public Object invoke(Object target, Method method, Object[] parameters) throws Throwable {
            if (method.getName().equals("equals")) {
                return parameters[0] != null && Proxy.isProxyClass(parameters[0].getClass())
                        && Proxy.getInvocationHandler(parameters[0]) == this;
            }
            if (method.getName().equals("hashCode")) {
                return hashCode();
            }
            if (method.getName().equals("toString")) {
                return String.format("%s broadcast", type.getSimpleName());
            }
            for (InvocationHandler handler : handlers.values()) {
                handler.invoke(null, method, parameters);
            }
            return null;
        }
    }

    private class ListenerInvocationHandler implements InvocationHandler {
        private final Object listener;

        public ListenerInvocationHandler(Object listener) {
            this.listener = listener;
        }

        public Object invoke(Object target, Method method, Object[] parameters) throws Throwable {
            try {
                method.invoke(listener, parameters);
            } catch (InvocationTargetException e) {
                throw new GradleException(getErrorMessage(), e.getCause());
            }
            return null;
        }
    }

    private class ClosureInvocationHandler implements InvocationHandler {
        private final String methodName;
        private final Closure closure;

        public ClosureInvocationHandler(String methodName, Closure closure) {
            this.methodName = methodName;
            this.closure = closure;
        }

        public Object invoke(Object target, Method method, Object[] parameters) throws Throwable {
            if (method.getName().equals(methodName)) {
                if (closure.getMaximumNumberOfParameters() < parameters.length) {
                    parameters = Arrays.asList(parameters).subList(0, closure.getMaximumNumberOfParameters())
                            .toArray();
                }
                try {
                    closure.call(parameters);
                } catch (InvokerInvocationException e) {
                    ScriptSource source = findSource(closure);
                    if (source != null) {
                        throw new GradleScriptException(getErrorMessage(), e.getCause(), source);
                    }
                    throw new GradleException(getErrorMessage(), e.getCause());
                }
            }
            return null;
        }

        private ScriptSource findSource(Closure closure) {
            Closure c = closure;
            while (c != null) {
                if (c.getOwner() instanceof ScriptWithSource) {
                    return ((ScriptWithSource) c.getOwner()).getScriptSource();
                }
                if (c.getOwner() instanceof Closure) {
                    c = (Closure) c.getOwner();
                } else {
                    c = null;
                }
            }
            return null;
        }
    }
}
