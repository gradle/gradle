/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.xdcl.ecosystem;

import org.gradle.api.internal.classpath.ModuleRegistry;
import org.gradle.xdcl.provider.XdclSchemaContributions;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Reusable contribution glue for built-in XDCL ecosystem plugins. A built-in ecosystem ships in the
 * distribution and is applied by id, so its jar never lands on the settings plugin classpath and the
 * transform-over-classpath schema discovery is blind to it. This glue lets the ecosystem plugin push
 * its schemas in instead: read its own manifest (written by the {@code gradlebuild.xdcl-ecosystem}
 * build convention), resolve the named distribution module(s) to their transitive jar closure via
 * the {@link ModuleRegistry}, keep the schema-carrying ones, and hand them to
 * {@link XdclSchemaContributions} — which the settings apply drains into the registry assembly.
 *
 * <p>Not JVM-specific: every built-in ecosystem plugin calls this with its own plugin id. Intended to
 * move to a shared distribution module once a second ecosystem exists; it lives here in the prototype.
 */
public final class XdclEcosystemSupport {

    private static final String MANIFEST_PREFIX = "META-INF/xdcl-ecosystem/";
    private static final String SCHEMA_PREFIX = "META-INF/xdcl/";
    private static final String SCHEMA_SUFFIX = ".xdsl";

    private XdclEcosystemSupport() {
    }

    /**
     * Contributes the schema jars of {@code pluginId}'s ecosystem to the current build's registry.
     *
     * @param pluginId       the ecosystem plugin id; its manifest is {@code META-INF/xdcl-ecosystem/<pluginId>.properties}
     * @param manifestLoader the classloader carrying that manifest (the ecosystem plugin's own loader)
     * @param moduleRegistry resolves each named module to its transitive jar closure
     * @param contributions  the build-scoped sink the settings apply drains
     */
    public static void contributeSchemas(
        String pluginId,
        ClassLoader manifestLoader,
        ModuleRegistry moduleRegistry,
        XdclSchemaContributions contributions
    ) {
        List<File> schemaJars = new ArrayList<>();
        for (String moduleName : readSchemaModules(pluginId, manifestLoader)) {
            for (File jar : moduleRegistry.getRuntimeClasspath(moduleName).getAsFiles()) {
                if (carriesSchemas(jar)) {
                    schemaJars.add(jar);
                }
            }
        }
        contributions.contribute(schemaJars);
    }

    private static List<String> readSchemaModules(String pluginId, ClassLoader loader) {
        String resource = MANIFEST_PREFIX + pluginId + ".properties";
        try (InputStream in = loader.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("xdcl-ecosystem manifest not found on the classpath: " + resource);
            }
            Properties props = new Properties();
            props.load(in);
            List<String> modules = new ArrayList<>();
            for (String name : props.getProperty("schemaModules", "").split(",")) {
                String trimmed = name.trim();
                if (!trimmed.isEmpty()) {
                    modules.add(trimmed);
                }
            }
            return modules;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read xdcl-ecosystem manifest: " + resource, e);
        }
    }

    private static boolean carriesSchemas(File jar) {
        if (!jar.isFile()) {
            return false;
        }
        try (JarFile jarFile = new JarFile(jar)) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (name.startsWith(SCHEMA_PREFIX) && name.endsWith(SCHEMA_SUFFIX)) {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            return false;
        }
    }
}
