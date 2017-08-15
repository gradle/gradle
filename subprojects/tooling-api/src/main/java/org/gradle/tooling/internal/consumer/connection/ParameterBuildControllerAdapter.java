/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.tooling.internal.consumer.connection;

import org.gradle.api.Action;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.UnknownModelException;
import org.gradle.tooling.UnsupportedVersionException;
import org.gradle.tooling.internal.adapter.ObjectGraphAdapter;
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter;
import org.gradle.tooling.internal.adapter.ViewBuilder;
import org.gradle.tooling.internal.consumer.versioning.ModelMapping;
import org.gradle.tooling.internal.gradle.DefaultProjectIdentifier;
import org.gradle.tooling.internal.protocol.BuildResult;
import org.gradle.tooling.internal.protocol.InternalBuildControllerVersion2;
import org.gradle.tooling.internal.protocol.InternalUnsupportedModelException;
import org.gradle.tooling.internal.protocol.ModelIdentifier;
import org.gradle.tooling.model.Model;
import org.gradle.tooling.model.ProjectModel;
import org.gradle.tooling.model.internal.Exceptions;

import java.io.File;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

public class ParameterBuildControllerAdapter extends AbstractBuildController implements BuildController {
    private final InternalBuildControllerVersion2 buildController;
    private final ProtocolToModelAdapter adapter;
    private final ObjectGraphAdapter resultAdapter;
    private final ModelMapping modelMapping;
    private final File rootDir;

    public ParameterBuildControllerAdapter(ProtocolToModelAdapter adapter, InternalBuildControllerVersion2 buildController, ModelMapping modelMapping, File rootDir) {
        this.adapter = adapter;
        this.buildController = buildController;
        this.modelMapping = modelMapping;
        this.rootDir = rootDir;
        // Treat all models returned to the action as part of the same object graph
        resultAdapter = adapter.newGraph();
    }

    @Override
    public <T, P> T getModel(Model target, Class<T> modelType, Class<P> parameterType, Action<? super P> parameterInitializer) throws UnsupportedVersionException {
        ModelIdentifier modelIdentifier = modelMapping.getModelIdentifierFromModelType(modelType);
        Object originalTarget = target == null ? null : adapter.unpack(target);

        if ((parameterType == null && parameterInitializer != null) || (parameterType != null && parameterInitializer == null) || (parameterType != null && !BeanProxy.isValid(parameterType))) {
            throw new UnknownModelException("Invalid parameter.");
        }

        P parameter = null;
        if (parameterType != null) {
            parameter = parameterType.cast(Proxy.newProxyInstance(parameterType.getClassLoader(), new Class[]{parameterType}, new BeanProxy()));
            parameterInitializer.execute(parameter);
        }

        BuildResult<?> result;
        try {
            result = buildController.getModel(originalTarget, modelIdentifier, parameter);
        } catch (InternalUnsupportedModelException e) {
            throw Exceptions.unknownModel(modelType, e);
        }

        ViewBuilder<T> viewBuilder = resultAdapter.builder(modelType);
        applyCompatibilityMapping(viewBuilder, new DefaultProjectIdentifier(rootDir, getProjectPath(target)));
        return viewBuilder.build(result.getModel());
    }

    private String getProjectPath(Model target) {
        if (target instanceof ProjectModel) {
            return ((ProjectModel) target).getProjectIdentifier().getProjectPath();
        } else {
            return ":";
        }
    }

    // TODO: use Gradle proxy instead of a common java proxy
    private static class BeanProxy implements InvocationHandler {
        private final Map<String, Object> properties = new HashMap<String, Object>();

        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (isSetter(method)) {
                properties.put(getPropertyName(method.getName()), args[0]);
            } else if (isGetter(method)) {
                return properties.get(getPropertyName(method.getName()));
            }
            return null;
        }

        private static boolean isValid(Class<?> clazz) {
            if (!clazz.isInterface()) {
                return false;
            }

            Map<String, Class<?>> setters = new HashMap<String, Class<?>>();
            Map<String, Class<?>> getters = new HashMap<String, Class<?>>();

            for (Method method : clazz.getDeclaredMethods()) {
                if (isGetter(method)) {
                    String property = getPropertyName(method.getName());
                    if (getters.containsKey(property)) {
                        return false;
                    }
                    getters.put(property, method.getReturnType());
                } else if (isSetter(method)) {
                    String property = getPropertyName(method.getName());
                    if (setters.containsKey(property)) {
                        return false;
                    }
                    setters.put(property, method.getParameterTypes()[0]);
                } else {
                    return false;
                }
            }

            if (setters.size() != getters.size()) {
                return false;
            }

            for (String property : setters.keySet()) {
                if (!getters.containsKey(property) || !setters.get(property).equals(getters.get(property))) {
                    return false;
                }
            }

            return true;
        }

        private static boolean isGetter(Method method) {
            String methodName = method.getName();
            return (isPrefixable(methodName, "get") || isPrefixable(methodName, "is")) && method.getParameterTypes().length == 0 && !method.getReturnType().equals(void.class);
        }

        private static boolean isSetter(Method method) {
            Class<?>[] parameterTypes = method.getParameterTypes();
            return isPrefixable(method.getName(), "set") && parameterTypes.length == 1 && !parameterTypes[0].equals(void.class) && method.getReturnType().equals(void.class);
        }

        private static boolean isPrefixable(String methodName, String prefix) {
            return methodName.startsWith(prefix) && methodName.length() > prefix.length() && Character.isUpperCase(methodName.charAt(prefix.length()));
        }

        private static String getPropertyName(String methodName) {
            if (methodName.startsWith("get")) {
                return getPropertyName(methodName, "get");
            } else if (methodName.startsWith("is")) {
                return getPropertyName(methodName, "is");
            } else if (methodName.startsWith("set")) {
                return getPropertyName(methodName, "set");
            }
            return null;
        }

        private static String getPropertyName(String methodName, String prefix) {
            String property = methodName.replaceFirst(prefix, "");
            return Character.toLowerCase(property.charAt(0)) + property.substring(1);
        }
    }
}
