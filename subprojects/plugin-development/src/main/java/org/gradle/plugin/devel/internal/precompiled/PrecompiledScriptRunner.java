/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.plugin.devel.internal.precompiled;

import org.gradle.api.Project;
import org.gradle.api.initialization.Settings;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.invocation.Gradle;
import org.gradle.groovy.scripts.BasicScript;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.groovy.scripts.TextResourceScriptSource;
import org.gradle.groovy.scripts.internal.CompiledScript;
import org.gradle.groovy.scripts.internal.ScriptRunnerFactory;
import org.gradle.internal.resource.StringTextResource;
import org.gradle.internal.service.ServiceRegistry;

import javax.annotation.Nullable;

public class PrecompiledScriptRunner {

    private final Object target;
    private final ServiceRegistry serviceRegistry;

    private final ScriptRunnerFactory scriptRunnerFactory;

    private final ClassLoaderScope classLoaderScope;

    @SuppressWarnings("unused")
    public PrecompiledScriptRunner(Project project) {
        this(project, ((ProjectInternal) project).getServices(), ((ProjectInternal) project).getClassLoaderScope());
    }

    @SuppressWarnings("unused")
    public PrecompiledScriptRunner(Settings settings) {
        this(settings, ((SettingsInternal) settings).getGradle().getServices(), ((SettingsInternal) settings).getClassLoaderScope());
    }

    @SuppressWarnings("unused")
    public PrecompiledScriptRunner(Gradle gradle) {
        this(gradle, ((GradleInternal) gradle).getServices(), ((GradleInternal) gradle).getClassLoaderScope());
    }

    private PrecompiledScriptRunner(Object target, ServiceRegistry serviceRegistry, ClassLoaderScope classLoaderScope) {
        this.target = target;
        this.serviceRegistry = serviceRegistry;

        this.scriptRunnerFactory = serviceRegistry.get(ScriptRunnerFactory.class);

        this.classLoaderScope = classLoaderScope.createChild("pre-compiled-script").lock();
    }

    public void run(@Nullable Class<?> scriptClass) {
        if (scriptClass != null) {
            executeScript(scriptClass);
        }
    }

    private void executeScript(Class<?> scriptClass) {
        scriptRunnerFactory.create(new CompiledGroovyPlugin(scriptClass), scriptSource(scriptClass), classLoaderScope.getExportClassLoader())
            .run(target, serviceRegistry);
    }


    private static ScriptSource scriptSource(Class<?> scriptClass) {
        return new TextResourceScriptSource(new StringTextResource(scriptClass.getSimpleName(), ""));
    }

    private class CompiledGroovyPlugin implements CompiledScript<BasicScript, Object> {

        private final Class<?> scriptClass;

        private CompiledGroovyPlugin(Class<?> scriptClass) {
            this.scriptClass = scriptClass;
        }

        @Override
        public boolean getRunDoesSomething() {
            return true;
        }

        @Override
        public boolean getHasMethods() {
            return false;
        }

        @Override
        public Class<? extends BasicScript> loadClass() {
            return scriptClass.asSubclass(BasicScript.class);
        }

        @Nullable
        @Override
        public Object getData() {
            return null;
        }

        @Override
        public void onReuse() {
            classLoaderScope.onReuse();
        }
    }

}
