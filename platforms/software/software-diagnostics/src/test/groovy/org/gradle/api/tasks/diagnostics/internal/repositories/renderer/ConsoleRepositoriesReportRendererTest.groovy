/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */
package org.gradle.api.tasks.diagnostics.internal.repositories.renderer

import org.gradle.api.tasks.diagnostics.internal.repositories.model.ReportContentFilter
import org.gradle.api.tasks.diagnostics.internal.repositories.model.ReportRepository
import org.gradle.api.tasks.diagnostics.internal.repositories.model.RepositoryDeclarationSite
import org.gradle.api.tasks.diagnostics.internal.repositories.model.RepositoryReportFullModel
import org.gradle.api.tasks.diagnostics.internal.repositories.model.RepositoryReportProjectModel
import org.gradle.api.tasks.diagnostics.internal.repositories.model.RepositoryReportSettingsModel
import org.gradle.api.tasks.diagnostics.internal.repositories.model.RepositoryRole
import org.gradle.api.tasks.diagnostics.internal.repositories.model.RepositoryType
import org.gradle.api.tasks.diagnostics.internal.repositories.reachability.ReachabilityStatus
import org.gradle.api.tasks.diagnostics.internal.repositories.spec.RepositoriesReportSpec
import org.gradle.internal.logging.text.TestStyledTextOutput
import org.gradle.util.Path
import spock.lang.Specification

import java.util.TreeMap

import static org.gradle.api.tasks.diagnostics.internal.repositories.model.RepositoryDeclarationSite.Scope.PROJECT
import static org.gradle.api.tasks.diagnostics.internal.repositories.model.RepositoryDeclarationSite.Scope.SETTINGS

class ConsoleRepositoriesReportRendererTest extends Specification {
    def output = new TestStyledTextOutput()

    def "renders completely empty model with top-level empty message"() {
        given:
        def model = new RepositoryReportFullModel(
            new RepositoryReportSettingsModel([], [], []),
            new TreeMap<>(RepositoryReportFullModel.pathComparator())
        )
        def renderer = new ConsoleRepositoriesReportRenderer(new RepositoriesReportSpec(null))

        when:
        renderer.render(model, output)

        then:
        def text = output.getRawValue()
        text.contains("There are no repositories present.")
        !text.contains("All Repositories")
        !text.contains("Repositories by Location")
        !text.contains("(none)")
        !text.contains("Legend")
    }

    def "renders filtered empty model with project-specific empty message"() {
        given:
        def projects = new TreeMap<>(RepositoryReportFullModel.pathComparator())
        projects.put(Path.path(":app"),
            new RepositoryReportProjectModel(Path.path(":app"), "app", [], []))
        def model = new RepositoryReportFullModel(
            new RepositoryReportSettingsModel([], [], []),
            projects
        )
        def renderer = new ConsoleRepositoriesReportRenderer(new RepositoriesReportSpec(":app"))

        when:
        renderer.render(model, output)

        then:
        def text = output.getRawValue()
        text.contains("There are no repositories present in project ':app'.")
        !text.contains("All Repositories")
        !text.contains("Repositories by Location")
        !text.contains("(none)")
    }

    def "renders single PLUGINS repo in All Repositories section with correct numbering"() {
        given:
        def site = new RepositoryDeclarationSite(SETTINGS, null, "pluginManagement.repositories")
        def repo = new ReportRepository(
            "MavenRepo", RepositoryType.MAVEN, "https://repo.maven.apache.org/maven2/",
            true, [], false, ReportContentFilter.EMPTY, [RepositoryRole.PLUGINS] as Set, site
        )
        def settings = new RepositoryReportSettingsModel([repo], [], [])
        def projects = new TreeMap<>(RepositoryReportFullModel.pathComparator())
        projects.put(Path.path(":app"),
            new RepositoryReportProjectModel(Path.path(":app"), "app", [], []))
        def model = new RepositoryReportFullModel(settings, projects)
        def renderer = new ConsoleRepositoriesReportRenderer(new RepositoriesReportSpec(null))

        when:
        renderer.render(model, output)

        then:
        def text = output.getRawValue()
        text.contains("All Repositories")
        text.contains("MavenRepo (1)")
        text.contains("Location:")
        text.contains("https://repo.maven.apache.org/maven2/")
        text.contains("Roles:")
        text.contains("PLUGINS")
        text.contains("Defined in:")
        text.contains("settings > pluginManagement.repositories")
        text.contains("Repositories by Location")
        text.contains("settings uses")
        text.contains("project ':app' uses")
        text.contains("- MavenRepo (1)")
        !text.contains("(none)")
        !text.contains("Legend")
    }

    def "renders Legend when identical declarations present"() {
        given:
        def content = ReportContentFilter.EMPTY
        def site1 = new RepositoryDeclarationSite(PROJECT, Path.path(":app"), "repositories")
        def site2 = new RepositoryDeclarationSite(PROJECT, Path.path(":lib"), "repositories")
        def repo1 = new ReportRepository(
            "MavenRepo", RepositoryType.MAVEN, "https://repo.maven.apache.org/maven2/",
            true, [], false, content, [RepositoryRole.PROJECT_DEPENDENCIES] as Set, site1)
        def repo2 = new ReportRepository(
            "MavenRepo", RepositoryType.MAVEN, "https://repo.maven.apache.org/maven2/",
            true, [], false, content, [RepositoryRole.PROJECT_DEPENDENCIES] as Set, site2)
        def projects = new TreeMap<>(RepositoryReportFullModel.pathComparator())
        projects.put(Path.path(":app"),
            new RepositoryReportProjectModel(Path.path(":app"), "app", [], [repo1]))
        projects.put(Path.path(":lib"),
            new RepositoryReportProjectModel(Path.path(":lib"), "lib", [], [repo2]))
        def settings = new RepositoryReportSettingsModel([], [], [])
        def model = new RepositoryReportFullModel(settings, projects)
        def renderer = new ConsoleRepositoriesReportRenderer(new RepositoriesReportSpec(null))

        when:
        renderer.render(model, output)

        then:
        def text = output.getRawValue()
        text.contains("MavenRepo (1) *")
        text.contains("MavenRepo (2) *")
        text.contains("Legend")
        text.contains("Identical repository declaration")
    }

    def "--project filter suppresses settings block and limits Repositories by Location to the filtered project"() {
        given:
        def site = new RepositoryDeclarationSite(PROJECT, Path.path(":app"), "repositories")
        def repo = new ReportRepository(
            "RepoA", RepositoryType.MAVEN, "https://a.example/",
            true, [], false, ReportContentFilter.EMPTY,
            [RepositoryRole.PROJECT_DEPENDENCIES] as Set, site)
        def projects = new TreeMap<>(RepositoryReportFullModel.pathComparator())
        projects.put(Path.path(":app"),
            new RepositoryReportProjectModel(Path.path(":app"), "app", [], [repo]))
        projects.put(Path.path(":lib"),
            new RepositoryReportProjectModel(Path.path(":lib"), "lib", [], []))
        def settings = new RepositoryReportSettingsModel([], [], [])
        def model = new RepositoryReportFullModel(settings, projects)
        def renderer = new ConsoleRepositoriesReportRenderer(new RepositoriesReportSpec(":app"))

        when:
        renderer.render(model, output)

        then:
        def text = output.getRawValue()
        text.contains("All Repositories")
        text.contains("RepoA (1)")
        text.contains("Repositories by Location")
        text.contains("project ':app' uses")
        text.contains("- RepoA (1)")
        !text.contains("settings uses")
        !text.contains(":lib")
    }

    def "renders settings-declared buildscript repo in All Repositories and under settings uses"() {
        given:
        def site = new RepositoryDeclarationSite(SETTINGS, null, "buildscript.repositories")
        def repo = new ReportRepository(
            "BuildRepo", RepositoryType.MAVEN, "https://corp.example.com/buildscript/",
            true, [], false, ReportContentFilter.EMPTY,
            [RepositoryRole.SETTINGS_BUILDSCRIPT_DEPENDENCIES] as Set, site
        )
        def settings = new RepositoryReportSettingsModel([], [repo], [])
        def projects = new TreeMap<>(RepositoryReportFullModel.pathComparator())
        def model = new RepositoryReportFullModel(settings, projects)
        def renderer = new ConsoleRepositoriesReportRenderer(new RepositoriesReportSpec(null))

        when:
        renderer.render(model, output)

        then:
        def text = output.getRawValue()
        text.contains("All Repositories")
        text.contains("BuildRepo (1)")
        text.contains("SETTINGS_BUILDSCRIPT_DEPENDENCIES")
        text.contains("settings > buildscript.repositories")
        text.contains("Repositories by Location")
        text.contains("settings uses")
        text.contains("- BuildRepo (1)")
    }

    def "--project filter includes PLUGINS and DRM repos from settings but no settings uses block"() {
        given:
        def pluginSite = new RepositoryDeclarationSite(SETTINGS, null, "pluginManagement.repositories")
        def drmSite = new RepositoryDeclarationSite(SETTINGS, null, "dependencyResolutionManagement.repositories")
        def projSite = new RepositoryDeclarationSite(PROJECT, Path.path(":app"), "repositories")
        def pluginRepo = new ReportRepository(
            "PluginPortal", RepositoryType.MAVEN, "https://plugins.gradle.org/m2/",
            true, [], false, ReportContentFilter.EMPTY,
            [RepositoryRole.PLUGINS] as Set, pluginSite
        )
        def drmRepo = new ReportRepository(
            "DrmRepo", RepositoryType.MAVEN, "https://repo.maven.apache.org/maven2/",
            true, [], false, ReportContentFilter.EMPTY,
            [RepositoryRole.PROJECT_DEPENDENCIES] as Set, drmSite
        )
        def appRepo = new ReportRepository(
            "AppRepo", RepositoryType.MAVEN, "https://app.example.com/",
            true, [], false, ReportContentFilter.EMPTY,
            [RepositoryRole.PROJECT_DEPENDENCIES] as Set, projSite
        )
        def settings = new RepositoryReportSettingsModel([pluginRepo], [], [drmRepo])
        def projects = new TreeMap<>(RepositoryReportFullModel.pathComparator())
        projects.put(Path.path(":app"),
            new RepositoryReportProjectModel(Path.path(":app"), "app", [], [appRepo]))
        def model = new RepositoryReportFullModel(settings, projects)
        def renderer = new ConsoleRepositoriesReportRenderer(new RepositoriesReportSpec(":app"))

        when:
        renderer.render(model, output)

        then:
        def text = output.getRawValue()
        text.contains("PluginPortal (1)")
        text.contains("DrmRepo (2)")
        text.contains("AppRepo (3)")
        text.contains("project ':app' uses")
        !text.contains("settings uses")
    }

    def "omits project blocks in Repositories by Location when their reference lists are empty"() {
        given:
        def projSite = new RepositoryDeclarationSite(PROJECT, Path.path(":app"), "repositories")
        def appRepo = new ReportRepository(
            "AppRepo", RepositoryType.MAVEN, "https://app.example.com/",
            true, [], false, ReportContentFilter.EMPTY,
            [RepositoryRole.PROJECT_DEPENDENCIES] as Set, projSite
        )
        def settings = new RepositoryReportSettingsModel([], [], [])
        def projects = new TreeMap<>(RepositoryReportFullModel.pathComparator())
        projects.put(Path.path(":app"),
            new RepositoryReportProjectModel(Path.path(":app"), "app", [], [appRepo]))
        projects.put(Path.path(":lib"),
            new RepositoryReportProjectModel(Path.path(":lib"), "lib", [], []))
        def model = new RepositoryReportFullModel(settings, projects)
        def renderer = new ConsoleRepositoriesReportRenderer(new RepositoriesReportSpec(null))

        when:
        renderer.render(model, output)

        then:
        def text = output.getRawValue()
        text.contains("Repositories by Location")
        text.contains("project ':app' uses")
        text.contains("- AppRepo (1)")
        // settings has no declared repos — block is suppressed
        !text.contains("settings uses")
        // :lib has no refs — block is suppressed
        !text.contains("project ':lib' uses")
        !text.contains("(none)")
    }

    def "omits empty project blocks but keeps settings uses when only settings.buildscript present"() {
        given:
        // A settings.buildscript repo — goes into All Repositories and under "settings uses" as a bucket.
        // Projects have no refs (no PLUGINS, no DRM, no project repos).
        def settingsSite = new RepositoryDeclarationSite(SETTINGS, null, "buildscript.repositories")
        def settingsBuildscriptRepo = new ReportRepository(
            "BuildRepo", RepositoryType.MAVEN, "https://corp.example.com/buildscript/",
            true, [], false, ReportContentFilter.EMPTY,
            [RepositoryRole.SETTINGS_BUILDSCRIPT_DEPENDENCIES] as Set, settingsSite
        )
        def settings = new RepositoryReportSettingsModel([], [settingsBuildscriptRepo], [])
        def projects = new TreeMap<>(RepositoryReportFullModel.pathComparator())
        projects.put(Path.path(":app"),
            new RepositoryReportProjectModel(Path.path(":app"), "app", [], []))
        projects.put(Path.path(":lib"),
            new RepositoryReportProjectModel(Path.path(":lib"), "lib", [], []))
        def model = new RepositoryReportFullModel(settings, projects)
        def renderer = new ConsoleRepositoriesReportRenderer(new RepositoriesReportSpec(null))

        when:
        renderer.render(model, output)

        then:
        def text = output.getRawValue()
        text.contains("All Repositories")
        text.contains("BuildRepo (1)")
        text.contains("Repositories by Location")
        text.contains("settings uses")
        text.contains("- BuildRepo (1)")
        // Per-project blocks are suppressed because they inherit no PLUGINS/DRM and declare nothing
        !text.contains("project ':app' uses")
        !text.contains("project ':lib' uses")
        !text.contains("(none)")
    }

    def "suppresses Repositories by Location heading entirely when --project target has empty reference list"() {
        given:
        // Some repo exists in another project so the top-level empty-model short-circuit does not trigger.
        def otherSite = new RepositoryDeclarationSite(PROJECT, Path.path(":other"), "repositories")
        def otherRepo = new ReportRepository(
            "OtherRepo", RepositoryType.MAVEN, "https://other.example/",
            true, [], false, ReportContentFilter.EMPTY,
            [RepositoryRole.PROJECT_DEPENDENCIES] as Set, otherSite
        )
        def settings = new RepositoryReportSettingsModel([], [], [])
        def projects = new TreeMap<>(RepositoryReportFullModel.pathComparator())
        projects.put(Path.path(":foo"),
            new RepositoryReportProjectModel(Path.path(":foo"), "foo", [], []))
        projects.put(Path.path(":other"),
            new RepositoryReportProjectModel(Path.path(":other"), "other", [], [otherRepo]))
        def model = new RepositoryReportFullModel(settings, projects)
        def renderer = new ConsoleRepositoriesReportRenderer(new RepositoriesReportSpec(":foo"))

        when:
        renderer.render(model, output)

        then:
        def text = output.getRawValue()
        // The filtered "All Repositories" section still renders (with (none) content since :foo has no repos).
        text.contains("All Repositories")
        // Repositories by Location section is suppressed entirely because :foo has no refs.
        !text.contains("Repositories by Location")
        !text.contains("settings uses")
        !text.contains("project ':foo' uses")
        !text.contains("project ':other' uses")
    }

    def "renders (ur) marker and legend entry when a repo is UNREACHABLE"() {
        given:
        def site = new RepositoryDeclarationSite(PROJECT, Path.path(":app"), "repositories")
        def repo = new ReportRepository(
            "DeadRepo", RepositoryType.MAVEN, "http://127.0.0.1:1/",
            true, [], false, ReportContentFilter.EMPTY,
            [RepositoryRole.PROJECT_DEPENDENCIES] as Set, site)
        def projects = new TreeMap<>(RepositoryReportFullModel.pathComparator())
        projects.put(Path.path(":app"),
            new RepositoryReportProjectModel(Path.path(":app"), "app", [], [repo]))
        def model = new RepositoryReportFullModel(new RepositoryReportSettingsModel([], [], []), projects)
        def spec = new RepositoriesReportSpec(null, false,
            ["http://127.0.0.1:1/": ReachabilityStatus.UNREACHABLE])
        def renderer = new ConsoleRepositoriesReportRenderer(spec)

        when:
        renderer.render(model, output)

        then:
        def text = output.getRawValue()
        text.contains("DeadRepo (1)")
        text.contains("Location:   http://127.0.0.1:1/ (ur)")
        !text.contains("DeadRepo (1) (ur)")
        text.contains("Legend")
        text.contains("(ur) Unreachable")
        !text.contains("(ua) Unauthorized")
        !text.contains("(o)  Running in offline")
    }

    def "renders (ua) marker and legend entry when a repo is UNAUTHORIZED"() {
        given:
        def site = new RepositoryDeclarationSite(PROJECT, Path.path(":app"), "repositories")
        def repo = new ReportRepository(
            "AuthRepo", RepositoryType.MAVEN, "https://auth.example/",
            true, [], false, ReportContentFilter.EMPTY,
            [RepositoryRole.PROJECT_DEPENDENCIES] as Set, site)
        def projects = new TreeMap<>(RepositoryReportFullModel.pathComparator())
        projects.put(Path.path(":app"),
            new RepositoryReportProjectModel(Path.path(":app"), "app", [], [repo]))
        def model = new RepositoryReportFullModel(new RepositoryReportSettingsModel([], [], []), projects)
        def spec = new RepositoriesReportSpec(null, false,
            ["https://auth.example/": ReachabilityStatus.UNAUTHORIZED])
        def renderer = new ConsoleRepositoriesReportRenderer(spec)

        when:
        renderer.render(model, output)

        then:
        def text = output.getRawValue()
        text.contains("AuthRepo (1)")
        text.contains("Location:   https://auth.example/ (ua)")
        !text.contains("AuthRepo (1) (ua)")
        text.contains("Legend")
        text.contains("(ua) Unauthorized")
        !text.contains("(ur) Unreachable")
    }

    def "renders (o) marker on All Repositories heading and legend when offline"() {
        given:
        def site = new RepositoryDeclarationSite(PROJECT, Path.path(":app"), "repositories")
        def repo = new ReportRepository(
            "AnyRepo", RepositoryType.MAVEN, "https://any.example/",
            true, [], false, ReportContentFilter.EMPTY,
            [RepositoryRole.PROJECT_DEPENDENCIES] as Set, site)
        def projects = new TreeMap<>(RepositoryReportFullModel.pathComparator())
        projects.put(Path.path(":app"),
            new RepositoryReportProjectModel(Path.path(":app"), "app", [], [repo]))
        def model = new RepositoryReportFullModel(new RepositoryReportSettingsModel([], [], []), projects)
        def spec = new RepositoriesReportSpec(null, true, [:])
        def renderer = new ConsoleRepositoriesReportRenderer(spec)

        when:
        renderer.render(model, output)

        then:
        def text = output.getRawValue()
        text.contains("All Repositories (o)")
        text.contains("Legend")
        text.contains("(o)  Running in offline mode")
        // No per-repo reachability markers in offline mode.
        !text.contains("(ur)")
        !text.contains("(ua)")
        // Sanity: the repo itself still renders.
        text.contains("AnyRepo (1)")
    }

    def "legend is suppressed when no marker was emitted even if reachability map is provided"() {
        given:
        // A REACHABLE status must not trigger any marker — and therefore no legend.
        def site = new RepositoryDeclarationSite(PROJECT, Path.path(":app"), "repositories")
        def repo = new ReportRepository(
            "LiveRepo", RepositoryType.MAVEN, "https://live.example/",
            true, [], false, ReportContentFilter.EMPTY,
            [RepositoryRole.PROJECT_DEPENDENCIES] as Set, site)
        def projects = new TreeMap<>(RepositoryReportFullModel.pathComparator())
        projects.put(Path.path(":app"),
            new RepositoryReportProjectModel(Path.path(":app"), "app", [], [repo]))
        def model = new RepositoryReportFullModel(new RepositoryReportSettingsModel([], [], []), projects)
        def spec = new RepositoriesReportSpec(null, false,
            ["https://live.example/": ReachabilityStatus.REACHABLE])
        def renderer = new ConsoleRepositoriesReportRenderer(spec)

        when:
        renderer.render(model, output)

        then:
        def text = output.getRawValue()
        text.contains("LiveRepo (1)")
        !text.contains("(ur)")
        !text.contains("(ua)")
        !text.contains("(o)")
        !text.contains("Legend")
    }

    def "renders Credentials: PRESENT line when hasCredentials is true and omits it otherwise"() {
        given:
        def siteWithCreds = new RepositoryDeclarationSite(PROJECT, Path.path(":app"), "repositories")
        def siteWithoutCreds = new RepositoryDeclarationSite(PROJECT, Path.path(":app"), "repositories")
        def repoWithCreds = new ReportRepository(
            "SecuredRepo", RepositoryType.MAVEN, "https://corp.example.com/",
            true, [], true, ReportContentFilter.EMPTY,
            [RepositoryRole.PROJECT_DEPENDENCIES] as Set, siteWithCreds
        )
        def repoWithoutCreds = new ReportRepository(
            "OpenRepo", RepositoryType.MAVEN, "https://open.example.com/",
            true, [], false, ReportContentFilter.EMPTY,
            [RepositoryRole.PROJECT_DEPENDENCIES] as Set, siteWithoutCreds
        )
        def projects = new TreeMap<>(RepositoryReportFullModel.pathComparator())
        projects.put(Path.path(":app"),
            new RepositoryReportProjectModel(Path.path(":app"), "app", [], [repoWithCreds, repoWithoutCreds]))
        def settings = new RepositoryReportSettingsModel([], [], [])
        def model = new RepositoryReportFullModel(settings, projects)
        def renderer = new ConsoleRepositoriesReportRenderer(new RepositoriesReportSpec(null))

        when:
        renderer.render(model, output)

        then:
        def text = output.getRawValue()
        text.contains("SecuredRepo (1)")
        text.contains("OpenRepo (2)")
        // Credentials line renders for the repo that has them
        text.contains("Credentials: PRESENT")
        // Exactly once — not for the other repo
        text.count("Credentials: PRESENT") == 1
    }

    def "All Repositories numbers settings buckets before per-project entries"() {
        given:
        def bsSite = new RepositoryDeclarationSite(SETTINGS, null, "buildscript.repositories")
        def plSite = new RepositoryDeclarationSite(SETTINGS, null, "pluginManagement.repositories")
        def drmSite = new RepositoryDeclarationSite(SETTINGS, null, "dependencyResolutionManagement.repositories")
        def appSite = new RepositoryDeclarationSite(PROJECT, Path.path(":app"), "repositories")
        def bsRepo = new ReportRepository(
            "SBD", RepositoryType.MAVEN, "https://sbd.example/",
            true, [], false, ReportContentFilter.EMPTY,
            [RepositoryRole.SETTINGS_BUILDSCRIPT_DEPENDENCIES] as Set, bsSite
        )
        def plRepo = new ReportRepository(
            "PL", RepositoryType.MAVEN, "https://pl.example/",
            true, [], false, ReportContentFilter.EMPTY,
            [RepositoryRole.PLUGINS] as Set, plSite
        )
        def drmRepo = new ReportRepository(
            "DRM", RepositoryType.MAVEN, "https://drm.example/",
            true, [], false, ReportContentFilter.EMPTY,
            [RepositoryRole.PROJECT_DEPENDENCIES] as Set, drmSite
        )
        def appRepo = new ReportRepository(
            "APP", RepositoryType.MAVEN, "https://app.example/",
            true, [], false, ReportContentFilter.EMPTY,
            [RepositoryRole.PROJECT_DEPENDENCIES] as Set, appSite
        )
        def settings = new RepositoryReportSettingsModel([plRepo], [bsRepo], [drmRepo])
        def projects = new TreeMap<>(RepositoryReportFullModel.pathComparator())
        projects.put(Path.path(":app"),
            new RepositoryReportProjectModel(Path.path(":app"), "app", [], [appRepo]))
        def model = new RepositoryReportFullModel(settings, projects)
        def renderer = new ConsoleRepositoriesReportRenderer(new RepositoriesReportSpec(null))

        when:
        renderer.render(model, output)

        then:
        def text = output.getRawValue()
        // Ordering: SBD(1) -> PL(2) -> DRM(3) -> APP(4)
        text.contains("SBD (1)")
        text.contains("PL (2)")
        text.contains("DRM (3)")
        text.contains("APP (4)")
    }
}
