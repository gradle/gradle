/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.nativeintegration;

import java.lang.reflect.Field;
import java.util.Map;

public class UnixReflectiveEnvVariableHandler implements EnvVariableHandler {
    public static final UnixReflectiveEnvVariableHandler INSTANCE = new UnixReflectiveEnvVariableHandler();

    @Override
    public void unsetenv(String name) {
        GradleEnvVariableHandler.INSTANCE.unsetenv(name);
        getEnv().remove(name);
    }

    @Override
    public void setenv(String name, String value) {
        GradleEnvVariableHandler.INSTANCE.setenv(name, value);
        getEnv().put(name, value);
    }

    @Override
    public EnvironmentModificationResult result() {
        return EnvironmentModificationResult.SUCCESS;
    }

    private Map<String, String> getEnv() {
        try {
            Map<String, String> theUnmodifiableEnvironment = System.getenv();
            Class<?> cu = theUnmodifiableEnvironment.getClass();
            Field m = cu.getDeclaredField("m");
            m.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, String> result = (Map<String, String>) m.get(theUnmodifiableEnvironment);
            return result;
        } catch (Exception e) {
            throw new NativeIntegrationException("Unable to get mutable environment map", e);
        }
    }
}
