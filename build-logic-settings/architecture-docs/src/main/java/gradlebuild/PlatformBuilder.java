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

import org.gradle.api.Action;
import org.gradle.api.initialization.Settings;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class PlatformBuilder extends ArchitectureElementBuilder {

    private final Settings settings;
    private final List<File> projectBaseDirs;
    private final ProjectScope projectScope;
    private final List<ArchitectureModuleBuilder> modules = new ArrayList<>();
    private final List<PlatformBuilder> uses = new ArrayList<>();

    public PlatformBuilder(String name, Settings settings, List<File> projectBaseDirs) {
        super(name);
        this.settings = settings;
        this.projectBaseDirs = projectBaseDirs;
        this.projectScope = new ProjectScope("platforms/" + name, settings, projectBaseDirs);
    }

    public void subproject(String projectName) {
        projectScope.subproject(projectName);
    }

    public void uses(PlatformBuilder platform) {
        uses.add(platform);
    }

    public void module(String platformName, Action<? super ArchitectureModuleBuilder> moduleConfiguration) {
        ArchitectureModuleBuilder module = new ArchitectureModuleBuilder(platformName, settings, projectBaseDirs);
        modules.add(module);
        moduleConfiguration.execute(module);
    }

    @Override
    public Platform build() {
        return new Platform(
            getName(),
            getId(),
            uses.stream().map(PlatformBuilder::getId).collect(Collectors.toList()),
            modules.stream().map(ArchitectureModuleBuilder::build).collect(Collectors.toList())
        );
    }
}
