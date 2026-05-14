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

package gradlebuild;

import org.gradle.api.Plugin;
import org.gradle.api.file.Directory;
import org.gradle.api.initialization.Settings;
import org.gradle.api.model.ObjectFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.Properties;

public abstract class VersionCatalogsPlugin implements Plugin<Settings> {

    @Inject
    protected abstract ObjectFactory getObjects();

    @Override
    public void apply(Settings settings) {
        settings.dependencyResolutionManagement(dependencyResolutionManagement -> {
            Directory rootDirectory = settings.getLayout().getRootDirectory();
            Directory root = settings.getRootProject().getName().startsWith("build-logic")
                ? rootDirectory.dir("..")
                : rootDirectory;
            Directory basePath = root.dir("gradle").dir("dependency-management");

            Properties sharedVersions = new Properties();
            try (InputStream input = Files.newInputStream(basePath.file("shared-versions.properties").getAsFile().toPath())) {
                sharedVersions.load(input);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }

            dependencyResolutionManagement.versionCatalogs(versionCatalogs -> {
                versionCatalogs.create("libs", catalog -> catalog.from(toml(basePath, "distribution.versions.toml")));
                versionCatalogs.create("providedLibs", catalog -> catalog.from(toml(basePath, "provided.versions.toml")));
                versionCatalogs.create("testLibs", catalog -> catalog.from(toml(basePath, "test.versions.toml")));
                versionCatalogs.create("buildLibs", catalog -> catalog.from(toml(basePath, "build.versions.toml")));
                versionCatalogs.all(catalog ->
                    sharedVersions.forEach((key, value) ->
                        catalog.version(key.toString(), value.toString())));
            });
        });
    }

    private Object toml(Directory basePath, String fileName) {
        return getObjects().fileCollection().from(basePath.file(fileName));
    }
}
