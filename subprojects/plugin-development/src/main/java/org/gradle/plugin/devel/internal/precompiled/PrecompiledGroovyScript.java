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

import com.google.common.base.CaseFormat;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.configuration.ScriptTarget;
import org.gradle.groovy.scripts.DelegatingScriptSource;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.groovy.scripts.TextResourceScriptSource;
import org.gradle.initialization.DefaultSettings;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.resource.TextFileResourceLoader;
import org.gradle.plugin.devel.PluginDeclaration;
import org.gradle.plugin.use.PluginId;
import org.gradle.plugin.use.internal.DefaultPluginId;

import java.io.File;

class PrecompiledGroovyScript {
    private static final String SCRIPT_PLUGIN_EXTENSION = ".gradle";

    private final ScriptSource scriptSource;
    private final Type type;
    private final PluginId pluginId;
    private final ScriptTarget scriptTarget;
    private final String precompiledScriptClassName;

    private enum Type {
        PROJECT(ProjectInternal.class, SCRIPT_PLUGIN_EXTENSION, "target.getServices()"),
        SETTINGS(DefaultSettings.class, ".settings.gradle", "target.getGradle().getServices()"),
        INIT(GradleInternal.class, ".init.gradle", "target.getServices()");

        private final Class<?> targetClass;
        private final String fileExtension;
        private final String serviceRegistryAccessCode;

        Type(Class<?> targetClass, String fileExtension, String serviceRegistryAccessCode) {
            this.targetClass = targetClass;
            this.fileExtension = fileExtension;
            this.serviceRegistryAccessCode = serviceRegistryAccessCode;
        }

        static Type getType(String fileName) {
            if (fileName.endsWith(SETTINGS.fileExtension)) {
                return SETTINGS;
            }
            if (fileName.endsWith(INIT.fileExtension)) {
                return INIT;
            }
            return PROJECT;
        }

        PluginId toPluginId(String fileName) {
            return DefaultPluginId.of(fileName.substring(0, fileName.indexOf(fileExtension)));
        }
    }

    PrecompiledGroovyScript(File scriptFile, TextFileResourceLoader resourceLoader) {
        String fileName = scriptFile.getName();
        this.type = Type.getType(fileName);
        this.pluginId = type.toPluginId(fileName);
        this.precompiledScriptClassName = toJavaIdentifier(kebabCaseToPascalCase(pluginId.getId().replace('.', '-')));
        this.scriptSource = new PrecompiledScriptPluginSource(scriptFile, precompiledScriptClassName, resourceLoader);
        this.scriptTarget = new PrecompiledScriptTarget(type != Type.INIT);
    }

    static void filterPluginFiles(PatternFilterable patternFilterable) {
        patternFilterable.include("**/*" + SCRIPT_PLUGIN_EXTENSION);
    }

    void declarePlugin(PluginDeclaration pluginDeclaration) {
        pluginDeclaration.setImplementationClass(getPluginAdapterClassName());
        pluginDeclaration.setId(pluginId.getId());
    }

    String getId() {
        return pluginId.getId();
    }

    String getPluginAdapterClassName() {
        return precompiledScriptClassName + "Plugin";
    }

    String getClassName() {
        return scriptSource.getClassName();
    }

    HashCode getContentHash() {
        return scriptSource.getResource().getContentHash();
    }

    ScriptSource getSource() {
        return scriptSource;
    }

    ScriptTarget getScriptTarget() {
        return scriptTarget;
    }

    String getTargetClassName() {
        return type.targetClass.getName();
    }

    public String serviceRegistryAccessCode() {
        return type.serviceRegistryAccessCode;
    }

    private static String kebabCaseToPascalCase(String s) {
        return CaseFormat.LOWER_HYPHEN.to(CaseFormat.UPPER_CAMEL, s);
    }

    private static String toJavaIdentifier(String s) {
        StringBuilder sb = new StringBuilder();
        if (!Character.isJavaIdentifierStart(s.charAt(0))) {
            sb.append("_");
        }
        for (char c : s.toCharArray()) {
            if (Character.isJavaIdentifierPart(c)) {
                sb.append(c);
            } else {
                sb.append("_");
            }
        }
        return sb.toString();
    }

    private static class PrecompiledScriptPluginSource extends DelegatingScriptSource {
        private final File scriptFile;
        private final String className;

        public PrecompiledScriptPluginSource(File scriptFile, String className, TextFileResourceLoader resourceLoader) {
            super(new TextResourceScriptSource(resourceLoader.loadFile("script", scriptFile)));
            this.scriptFile = scriptFile;
            this.className = "precompiled_" + className;
        }

        @Override
        public String getClassName() {
            return className;
        }

        @Override
        public String getFileName() {
            return scriptFile.getPath();
        }
    }
}
