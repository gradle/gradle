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
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.groovy.scripts.TextResourceScriptSource;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.resource.UriTextResource;
import org.gradle.plugin.use.PluginId;
import org.gradle.plugin.use.internal.DefaultPluginId;

import java.io.File;
import java.util.Optional;

class PreCompiledScript {
    public static final String SCRIPT_PLUGIN_EXTENSION = ".gradle";
    private static final String PLUGIN_PREFIX = "plugins";
    private static final String BUILDSCRIPT_PREFIX = "script";

    private final ScriptSource scriptSource;
    private final PluginId pluginId;

    PreCompiledScript(File scriptFile) {
        this.scriptSource = new TextResourceScriptSource(new UriTextResource("script", scriptFile));
        // TODO should also detect package name and add it to the id
        this.pluginId = DefaultPluginId.of(getFileNameWithoutExtension());
    }

    String getId() {
        return pluginId.getId();
    }

    Optional<String> getGeneratedPluginPackage() {
        return Optional.empty();
    }

    String getGeneratedPluginClassName() {
        return toJavaIdentifier(kebabCaseToPascalCase(pluginId.getName())) + "Plugin";
    }

    String getPluginMetadataDirPath() {
        return getClassName() + "/" + PLUGIN_PREFIX + "-metadata";
    }

    String getBuildScriptMetadataDirPath() {
        return getClassName() + "/" + BUILDSCRIPT_PREFIX + "-metadata";
    }

    String getPluginClassesDirPath() {
        return getClassName() + "/" + PLUGIN_PREFIX + "-classes";
    }

    String getBuildScriptClassesDirPath() {
        return getClassName() + "/" + BUILDSCRIPT_PREFIX + "-classes";
    }

    File getScriptFile() {
        return new File(scriptSource.getFileName());
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

    private String getFileNameWithoutExtension() {
        String fileName = new File(scriptSource.getFileName()).getName();
        return fileName.substring(0, fileName.indexOf(SCRIPT_PLUGIN_EXTENSION));
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
}
