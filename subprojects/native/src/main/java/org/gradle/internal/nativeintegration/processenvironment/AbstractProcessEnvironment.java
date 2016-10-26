/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.internal.nativeintegration.processenvironment;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.gradle.internal.nativeintegration.NativeIntegrationException;
import org.gradle.internal.nativeintegration.ProcessEnvironment;
import org.gradle.internal.nativeintegration.ReflectiveEnvironment;

import java.io.File;
import java.util.List;
import java.util.Map;

public abstract class AbstractProcessEnvironment implements ProcessEnvironment {
    //for updates to private JDK caches of the environment state
    private final ReflectiveEnvironment reflectiveEnvironment = new ReflectiveEnvironment();

    @Override
    public boolean maybeSetEnvironment(Map<String, String> source) {
        // need to take copy to prevent ConcurrentModificationException
        List<String> keysToRemove = Lists.newArrayList(Sets.difference(System.getenv().keySet(), source.keySet()));
        for (String key : keysToRemove) {
            removeEnvironmentVariable(key);
        }
        for (Map.Entry<String, String> entry : source.entrySet()) {
            setEnvironmentVariable(entry.getKey(), entry.getValue());
        }
        return true;
    }

    @Override
    public void removeEnvironmentVariable(String name) throws NativeIntegrationException {
        removeNativeEnvironmentVariable(name);
        reflectiveEnvironment.unsetenv(name);
    }

    protected abstract void removeNativeEnvironmentVariable(String name);

    @Override
    public boolean maybeRemoveEnvironmentVariable(String name) {
        removeEnvironmentVariable(name);
        return true;
    }

    @Override
    public void setEnvironmentVariable(String name, String value) throws NativeIntegrationException {
        if (value == null) {
            removeEnvironmentVariable(name);
            return;
        }

        setNativeEnvironmentVariable(name, value);
        reflectiveEnvironment.setenv(name, value);
    }

    protected abstract void setNativeEnvironmentVariable(String name, String value);

    @Override
    public boolean maybeSetEnvironmentVariable(String name, String value) {
        setEnvironmentVariable(name, value);
        return true;
    }

    @Override
    public void setProcessDir(File processDir) throws NativeIntegrationException {
        if (!processDir.exists()) {
            return;
        }

        setNativeProcessDir(processDir);
        System.setProperty("user.dir", processDir.getAbsolutePath());
    }

    protected abstract void setNativeProcessDir(File processDir);

    @Override
    public boolean maybeSetProcessDir(File processDir) {
        setProcessDir(processDir);
        return true;
    }

    @Override
    public Long maybeGetPid() {
        return getPid();
    }

    @Override
    public boolean maybeDetachProcess() {
        detachProcess();
        return true;
    }
}
