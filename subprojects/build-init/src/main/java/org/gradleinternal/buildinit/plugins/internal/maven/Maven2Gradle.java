/*
 * Copyright 2021 the original author or authors.
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

package org.gradleinternal.buildinit.plugins.internal.maven;

import org.apache.commons.lang.StringUtils;
import org.apache.maven.model.Exclusion;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.model.Repository;
import org.apache.maven.project.MavenProject;
import org.apache.maven.repository.RepositorySystem;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.gradle.api.JavaVersion;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.file.Directory;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.buildinit.InsecureProtocolOption;
import org.gradle.buildinit.plugins.internal.BuildScriptBuilder;
import org.gradle.buildinit.plugins.internal.BuildScriptBuilderFactory;
import org.gradle.buildinit.plugins.internal.DependenciesBuilder;
import org.gradle.buildinit.plugins.internal.ScriptBlockBuilder;
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitDsl;
import org.gradle.util.internal.RelativePathUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This script obtains the effective POM of the current project, reads its dependencies
 * and generates build.gradle scripts. It also generates settings.gradle for multi-module builds. <br>
 *
 * It currently supports both single-module and multi-module POMs, inheritance, dependency management and properties.
 */
public class Maven2Gradle {
    private final BuildScriptBuilderFactory scriptBuilderFactory;
    private final boolean useIncubatingAPIs;

    private final Set<MavenProject> allProjects;
    private final MavenProject rootProject;

    private final List<MavenProject> dependentWars = new ArrayList<>();
    private final Directory workingDir;
    private final BuildInitDsl dsl;
    private final InsecureProtocolOption insecureProtocolOption;

    public Maven2Gradle(Set<MavenProject> mavenProjects, Directory workingDir, BuildInitDsl dsl, boolean useIncubatingAPIs, InsecureProtocolOption insecureProtocolOption) {
        assert !mavenProjects.isEmpty(): "No Maven projects provided.";

        this.scriptBuilderFactory = new BuildScriptBuilderFactory(new DocumentationRegistry());
        this.useIncubatingAPIs = useIncubatingAPIs;
        this.allProjects = mavenProjects;
        this.rootProject = mavenProjects.iterator().next();
        this.workingDir = workingDir;
        this.dsl = dsl;
        this.insecureProtocolOption = insecureProtocolOption;
    }

    public void convert() {
        boolean multimodule = !rootProject.getModules().isEmpty();

        if (multimodule) {
            String groupId = rootProject.getGroupId();

            BuildScriptBuilder buildSrcScriptBuilder = scriptBuilderFactory.scriptForMavenConversion(dsl, "buildSrc/build", useIncubatingAPIs, insecureProtocolOption);
            buildSrcScriptBuilder.conventionPluginSupport("Support convention plugins written in " + dsl.toString() + ". Convention plugins are build scripts in 'src/main' that automatically become available as plugins in the main build.");
            buildSrcScriptBuilder.create(workingDir).generate();

            BuildScriptBuilder conventionPluginBuilder = scriptBuilderFactory.scriptForMavenConversion(dsl, "buildSrc/src/main/" + dsl.name().toLowerCase() + "/" + groupId + ".java-conventions", useIncubatingAPIs, insecureProtocolOption);

            generateSettings(rootProject.getArtifactId(), allProjects);

            Map<String, List<Dependency>> dependencies = new LinkedHashMap<>();
            for (MavenProject project : allProjects) {
                dependencies.put(project.getArtifactId(), getDependencies(project, allProjects));
            }

            coordinatesForProject(rootProject, conventionPluginBuilder);
            conventionPluginBuilder.plugin(null, "java-library");
            conventionPluginBuilder.plugin(null, "maven-publish");
            compilerSettings(rootProject, conventionPluginBuilder);

            repositoriesForProjects(allProjects, conventionPluginBuilder);
            globalExclusions(rootProject, conventionPluginBuilder);

            List<Dependency> commonDeps = dependencies.get(rootProject.getArtifactId());
            declareDependencies(commonDeps, conventionPluginBuilder);
            testNg(commonDeps, conventionPluginBuilder);
            configurePublishing(conventionPluginBuilder, packagesSources(rootProject), false, false);

            conventionPluginBuilder.create(workingDir).generate();

            for (MavenProject module : modules(allProjects, false)) {
                String id = module.getArtifactId();
                List<Dependency> moduleDependencies = dependencies.get(id);
                boolean warPack = module.getPackaging().equals("war");
                BuildScriptBuilder moduleScriptBuilder = scriptBuilderFactory.scriptForMavenConversion(dsl, RelativePathUtil.relativePath(workingDir.getAsFile(), projectDir(module)) + "/build", useIncubatingAPIs, insecureProtocolOption);

                moduleScriptBuilder.plugin(null, groupId + ".java-conventions");

                if (!module.getGroupId().equals(rootProject.getGroupId())) {
                    moduleScriptBuilder.propertyAssignment(null, "group", module.getGroupId());
                }

                if (warPack) {
                    moduleScriptBuilder.plugin(null, "war");
                    if (dependentWars.stream().anyMatch(project -> project.getGroupId().equals(module.getGroupId()) && project.getArtifactId().equals(id))) {
                        moduleScriptBuilder.taskPropertyAssignment(null, "jar", "Jar", "enabled", true);
                    }
                }

                descriptionForProject(module, moduleScriptBuilder);
                declareDependencies(moduleDependencies, moduleScriptBuilder);
                testNg(moduleDependencies, moduleScriptBuilder);

                if (packageTests(module, moduleScriptBuilder)) {
                    if (dsl == BuildInitDsl.GROOVY) {
                        moduleScriptBuilder.methodInvocation(null, "publishing.publications.maven.artifact", moduleScriptBuilder.propertyExpression("testsJar"));
                    } else {
                        moduleScriptBuilder.methodInvocation(null, "(publishing.publications[\"maven\"] as MavenPublication).artifact", moduleScriptBuilder.propertyExpression("testsJar"));
                    }
                }
                if (packagesJavadocs(module)) {
                    ScriptBlockBuilder javaExtension = moduleScriptBuilder.block(null, "java");
                    javaExtension.methodInvocation(null, "withJavadocJar");
                }

                moduleScriptBuilder.create(workingDir).generate();
            }
        } else {
            BuildScriptBuilder scriptBuilder = scriptBuilderFactory.scriptForMavenConversion(dsl, "build", useIncubatingAPIs, insecureProtocolOption);
            generateSettings(this.rootProject.getArtifactId(), Collections.emptySet());

            scriptBuilder.plugin(null, "java-library");
            scriptBuilder.plugin(null, "maven-publish");
            coordinatesForProject(this.rootProject, scriptBuilder);
            descriptionForProject(this.rootProject, scriptBuilder);
            compilerSettings(this.rootProject, scriptBuilder);
            globalExclusions(this.rootProject, scriptBuilder);
            boolean testsJarTaskGenerated = packageTests(this.rootProject, scriptBuilder);
            configurePublishing(scriptBuilder, packagesSources(this.rootProject), testsJarTaskGenerated, packagesJavadocs(this.rootProject));

            scriptBuilder.repositories().mavenLocal(null);
            Set<String> repoSet = new LinkedHashSet<>();
            getRepositoriesForModule(this.rootProject, repoSet);
            for (String repo : repoSet) {
                scriptBuilder.repositories().maven(null, repo);
            }

            List<Dependency> dependencies = getDependencies(this.rootProject, null);
            declareDependencies(dependencies, scriptBuilder);
            testNg(dependencies, scriptBuilder);

            scriptBuilder.create(workingDir).generate();
        }
    }

    private void configurePublishing(BuildScriptBuilder builder, boolean publishesSources, boolean testsJarTaskGenerated, boolean publishesJavadoc) {
        if (publishesSources || publishesJavadoc) {
            ScriptBlockBuilder javaExtension = builder.block(null, "java");
            if (publishesSources) {
                javaExtension.methodInvocation(null, "withSourcesJar");
            }
            if (publishesJavadoc) {
                javaExtension.methodInvocation(null, "withJavadocJar");
            }
        }
        ScriptBlockBuilder publishing = builder.block(null, "publishing");
        publishing.containerElement(null, "publications", "maven", "MavenPublication", p -> {
            p.methodInvocation(null, "from", builder.containerElementExpression("components", "java"));
            if (testsJarTaskGenerated) {
                p.methodInvocation(null, "artifact", builder.propertyExpression("testsJar"));
            }
        });
    }

    private void declareDependencies(List<Dependency> dependencies, BuildScriptBuilder builder) {
        DependenciesBuilder dependenciesBuilder = builder.dependencies();
        for (Dependency dep : dependencies) {
            if (dep instanceof ProjectDependency) {
                dependenciesBuilder.projectDependency(dep.getConfiguration(), null, ((ProjectDependency) dep).getProjectPath());
            } else {
                ExternalDependency extDep = (ExternalDependency) dep;
                dependenciesBuilder.dependency(dep.getConfiguration(), null, extDep.getGroupId() + ":" + extDep.getModule() + ":" + extDep.getVersion());
            }
        }
    }

    private void globalExclusions(MavenProject project, BuildScriptBuilder builder) {
        Plugin enforcerPlugin = plugin("maven-enforcer-plugin", project);
        PluginExecution enforceGoal = pluginGoal("enforce", enforcerPlugin);
        if (enforceGoal != null) {
            Xpp3Dom configuration = (Xpp3Dom) enforceGoal.getConfiguration();
            Xpp3Dom bannedDependencies = configuration.getChild("rules").getChild("bannedDependencies");
            if (bannedDependencies!=null) {
                Xpp3Dom[] children = bannedDependencies.getChild("excludes").getChildren();
                ScriptBlockBuilder block = builder.block(null, "configurations.all");
                if (children != null) {
                    for (Xpp3Dom exclude : children) {
                        String[] tokens = exclude.getValue().split(":");
                        Map<String, String> params = new LinkedHashMap<>();
                        params.put("group", tokens[0]);
                        if (tokens.length > 1 && !tokens[1].equals("*")) {
                            params.put("module", tokens[1]);
                        }
                        block.methodInvocation(null, "exclude", params);
                    }
                }
            }
        }
    }

    private void testNg(List<Dependency> moduleDependencies, BuildScriptBuilder builder) {
        boolean testng = moduleDependencies.stream().anyMatch(dep ->
            dep instanceof ExternalDependency && "org.testng".equals(((ExternalDependency) dep).getGroupId()) && "testng".equals(((ExternalDependency) dep).getModule())
        );
        if (testng) {
            builder.taskMethodInvocation(null, "test", "Test", "useTestNG");
        }
    }

    private Set<MavenProject> modules(Set<MavenProject> projects, boolean incReactors) {
        return projects.stream().filter(project -> {
            Optional<MavenProject> parentIsPartOfThisBuild = projects.stream().filter(proj ->
                project.getParent() == null || (project.getParent() != null && proj.getArtifactId().equals(project.getParent().getArtifactId()) && proj.getGroupId().equals(project.getParent().getGroupId()))
            ).findFirst();
            return parentIsPartOfThisBuild.isPresent() && (incReactors || !"pom".equals(project.getPackaging()));
        }).collect(Collectors.toSet());
    }

    private String fqn(MavenProject project, Set<MavenProject> allProjects) {
        StringBuilder buffer = new StringBuilder();
        generateFqn(project, allProjects, buffer);
        return buffer.toString();
    }

    private void generateFqn(MavenProject project, Set<MavenProject> allProjects, StringBuilder buffer) {
        String artifactId = project.getArtifactId();
        buffer.insert(0, ":" + artifactId);
        //we don't need the top-level parent in gradle, so we stop on it
        if (!getModuleIdentifier(rootProject).equals(getModuleIdentifier(project.getParent()))) {
            allProjects.stream().filter(proj ->
                getModuleIdentifier(proj) == getModuleIdentifier(project.getParent())
            ).findFirst().ifPresent(parentInBuild -> generateFqn(parentInBuild, allProjects, buffer));
        }
    }

    private ModuleIdentifier getModuleIdentifier(MavenProject project) {
        if (project == null) {
            return null;
        }
        String artifactId = project.getArtifactId();
        String groupId = StringUtils.isNotEmpty(project.getGroupId()) ? project.getGroupId() : project.getParent().getGroupId();
        return DefaultModuleIdentifier.newId(groupId, artifactId);
    }

    private void coordinatesForProject(MavenProject project, BuildScriptBuilder builder) {
        builder.propertyAssignment(null, "group", project.getGroupId());
        builder.propertyAssignment(null, "version", project.getVersion());
    }

    private void descriptionForProject(MavenProject project, BuildScriptBuilder builder) {
        if (StringUtils.isNotEmpty(project.getName())) {
            builder.propertyAssignment(null, "description", project.getName());
        }
    }

    private void repositoriesForProjects(Set<MavenProject> projects, BuildScriptBuilder builder) {
        builder.repositories().mavenLocal(null);
        Set<String> repoSet = new LinkedHashSet<>();
        for(MavenProject project : projects) {
            getRepositoriesForModule(project, repoSet);
        }
        for(String repo : repoSet) {
            builder.repositories().maven(null, repo);
        }
    }

    private void getRepositoriesForModule(MavenProject module, Set<String> repoSet) {
        for(Repository repo : module.getRepositories()) {
            if (repo.getId().equals(RepositorySystem.DEFAULT_REMOTE_REPO_ID)) {
                repoSet.add(RepositoryHandler.MAVEN_CENTRAL_URL);
            } else {
                repoSet.add(repo.getUrl());
            }
        }
        // No need to include plugin repos, as they won't be used by Gradle
    }

    private List<Dependency> getDependencies(MavenProject project, Set<MavenProject> allProjects) {
        List<org.apache.maven.model.Dependency> dependencies = new ArrayList<>();
        collectAllDependencies(project, dependencies);

        boolean war = "war".equals(project.getPackaging());

        List<org.apache.maven.model.Dependency> compileTimeScope = new ArrayList<>();
        List<org.apache.maven.model.Dependency> runtimeScope = new ArrayList<>();
        List<org.apache.maven.model.Dependency> testScope = new ArrayList<>();
        List<org.apache.maven.model.Dependency> providedScope = new ArrayList<>();
        List<org.apache.maven.model.Dependency> systemScope = new ArrayList<>();

        //cleanup duplicates from parent
        for(org.apache.maven.model.Dependency mavenDependency : dependencies) {
            if (!duplicateDependency(mavenDependency, project, allProjects)) {
                String scope = StringUtils.isNotEmpty(mavenDependency.getScope()) ? mavenDependency.getScope() : "compile";
                switch (scope) {
                    case "compile":
                        compileTimeScope.add(mavenDependency);
                        break;
                    case "test":
                        testScope.add(mavenDependency);
                        break;
                    case "provided":
                        providedScope.add(mavenDependency);
                        break;
                    case "runtime":
                        runtimeScope.add(mavenDependency);
                        break;
                    case "system":
                        systemScope.add(mavenDependency);
                        break;
                }
            }
        }

        List<Dependency> result = new ArrayList<>();
        if (!compileTimeScope.isEmpty() || !runtimeScope.isEmpty() || !testScope.isEmpty() || !providedScope.isEmpty() || !systemScope.isEmpty()) {
            if (!compileTimeScope.isEmpty()) {
                for (org.apache.maven.model.Dependency dep : compileTimeScope) {
                    createGradleDep("api", result, dep, war);
                }
            }
            if (!runtimeScope.isEmpty()) {
                for (org.apache.maven.model.Dependency dep : runtimeScope) {
                    createGradleDep("runtimeOnly", result, dep, war);
                }
            }
            if (!testScope.isEmpty()) {
                for (org.apache.maven.model.Dependency dep : testScope) {
                    createGradleDep("testImplementation", result, dep, war);
                }
            }
            if (!providedScope.isEmpty()) {
                for (org.apache.maven.model.Dependency dep : providedScope) {
                    createGradleDep("providedCompile", result, dep, war);
                }
            }
            if (!systemScope.isEmpty()) {
                for (org.apache.maven.model.Dependency dep : systemScope) {
                    createGradleDep("system", result, dep, war);
                }
            }
        }
        return result;
    }

    private void collectAllDependencies(MavenProject mavenProject, List<org.apache.maven.model.Dependency> dependencies) {
        if (mavenProject.getParent() != null) {
            collectAllDependencies(mavenProject.getParent(), dependencies);
        }
        dependencies.addAll(mavenProject.getDependencies());
    }

    /**
     * print function then checks the exclusions node to see if it exists, if
     * so it branches off, otherwise we call our simple print function
     */
    private void createGradleDep(String scope, List<Dependency> result, org.apache.maven.model.Dependency mavenDependency, boolean war) {
        Optional<MavenProject> projectDep = allProjects.stream().filter(prj ->
            prj.getArtifactId().equals(mavenDependency.getArtifactId()) && prj.getGroupId().equals(mavenDependency.getGroupId())
        ).findFirst();

        if (projectDep.isPresent()) {
            createProjectDependency(projectDep.get(), result, scope, allProjects);
        } else {
            if (!war && "providedCompile".equals(scope)) {
                scope = "compileOnly";
            }
            createExternalDependency(mavenDependency, result, scope);
        }
    }

    private void compilerSettings(MavenProject project, BuildScriptBuilder builder) {
        String source = "1.8";
        String target = "1.8";

        Plugin compilerPlugin = plugin("maven-compiler-plugin", project);
        if (compilerPlugin != null) {
            Xpp3Dom configuration = (Xpp3Dom) compilerPlugin.getConfiguration();
            if (configuration != null) {
                Xpp3Dom configuredSource = configuration.getChild("source");
                Xpp3Dom configuredTarget = configuration.getChild("target");
                if (configuredSource != null && configuredSource.getValue() != null && !configuredSource.getValue().trim().isEmpty()) {
                    source = configuredSource.getValue();
                }
                if (configuredTarget != null && configuredTarget.getValue() != null && !configuredTarget.getValue().trim().isEmpty()) {
                    target = configuredTarget.getValue();
                }
            }
        }

        builder.propertyAssignment(null, "java.sourceCompatibility", JavaVersion.toVersion(source));
        if (!target.equals(source)) {
            builder.propertyAssignment(null, "java.targetCompatibility", JavaVersion.toVersion(target));
        }

        String encoding = (String) project.getProperties().get("project.build.sourceEncoding");
        if (StringUtils.isNotEmpty(encoding)) {
            builder.taskPropertyAssignment(null, "JavaCompile", "options.encoding", encoding);
            builder.taskPropertyAssignment(null, "Javadoc", "options.encoding", encoding);
        }
    }

    private Plugin plugin(String artifactId, MavenProject project) {
        return project.getBuild().getPlugins().stream().filter(pluginTag ->
            pluginTag.getArtifactId().equals(artifactId)
        ).findFirst().orElse(null);
    }

    private PluginExecution pluginGoal(String goalName, Plugin plugin) {
        if (plugin == null) {
            return null;
        }
        return plugin.getExecutions().stream().filter(exec ->
            exec.getGoals().stream().anyMatch(gl -> gl.startsWith(goalName))
        ).findFirst().orElse(null);
    }

    boolean packagesSources(MavenProject project) {
        Plugin sourcePlugin = plugin("maven-source-plugin", project);
        return sourcePlugin != null && pluginGoal("jar", sourcePlugin) != null;
    }

    boolean packageTests(MavenProject project, BuildScriptBuilder builder) {
        Plugin jarPlugin = plugin("maven-jar-plugin", project);
        if (pluginGoal("test-jar", jarPlugin) != null) {
            builder.taskRegistration(null, "testsJar", "Jar", task -> {
                task.propertyAssignment(null, "archiveClassifier", "tests", false);
                task.methodInvocation(null, "from", builder.propertyExpression(builder.containerElementExpression("sourceSets", "test"), "output"));
            });
            return true;
        }
        return false;
    }

    boolean packagesJavadocs(MavenProject project) {
        Plugin jarPlugin = plugin("maven-javadoc-plugin", project);
        return jarPlugin != null && pluginGoal("jar", jarPlugin) != null;
    }

    private boolean duplicateDependency(org.apache.maven.model.Dependency dependency, MavenProject project, Set<MavenProject> allProjects) {
        MavenProject parent = project.getParent();
        if (allProjects == null || !allProjects.contains(parent)) { // simple project or no parent in the build
            return false;
        } else {
            boolean duplicate = parent.getDependencies().stream().anyMatch(dep ->
                dep.getGroupId().equals(dependency.getGroupId()) && dep.getArtifactId().equals(dependency.getArtifactId())
            );
            if (duplicate) {
                return true;
            } else {
                return duplicateDependency(dependency, parent, allProjects);
            }
        }
    }

    private File projectDir(MavenProject project) {
        return new File(project.getBuild().getDirectory()).getParentFile();
    }

    private void generateSettings(String mvnProjectName, Set<MavenProject> projects) {
        BuildScriptBuilder scriptBuilder = scriptBuilderFactory.scriptForMavenConversion(dsl, "settings", useIncubatingAPIs, insecureProtocolOption);

        scriptBuilder.propertyAssignment(null, "rootProject.name", mvnProjectName);
        Set<MavenProject> modules = modules(projects, true);

        List<String> moduleNames = new ArrayList<>();
        Map<String, String> artifactIdToDir = new LinkedHashMap<>();
        for (MavenProject project : modules) {
            String fqn = fqn(project, projects);
            File projectDirectory = projectDir(project);
            // don't add project if it's the rootproject
            if (!workingDir.getAsFile().equals(projectDirectory)) {
                moduleNames.add(fqn);

                // Calculate the path to the project, ignore this path if it's the default value
                String relativePath = RelativePathUtil.relativePath(workingDir.getAsFile(), projectDirectory);
                if (!fqn.equals(":" + relativePath)) {
                    artifactIdToDir.put(fqn, relativePath);
                }
            }
        }


        for (String name : moduleNames) {
            scriptBuilder.methodInvocation(null, "include", name);
        }
        for (Map.Entry<String, String> entry : artifactIdToDir.entrySet()) {
            BuildScriptBuilder.Expression dirExpression = scriptBuilder.methodInvocationExpression("file", entry.getValue());
            scriptBuilder.propertyAssignment(null, "project(\"" + entry.getKey() + "\").projectDir", dirExpression);
        }
        scriptBuilder.create(workingDir).generate();
    }

    private void createExternalDependency(org.apache.maven.model.Dependency mavenDependency, List<Dependency> result, String scope) {
        String classifier = mavenDependency.getClassifier();
        List<String> exclusions = mavenDependency.getExclusions().stream().map(Exclusion::getArtifactId).collect(Collectors.toList());
        result.add(new ExternalDependency(scope, mavenDependency.getGroupId(), mavenDependency.getArtifactId(), mavenDependency.getVersion(), classifier, exclusions));
    }

    private void createProjectDependency(MavenProject projectDep, List<Dependency> result, String scope, Set<MavenProject> allProjects) {
        if ("war".equals(projectDep.getPackaging())) {
            dependentWars.add(projectDep);
        }
        result.add(new ProjectDependency(scope, fqn(projectDep, allProjects)));
    }

}
