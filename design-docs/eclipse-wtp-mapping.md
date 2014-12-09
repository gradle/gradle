A proposed change to how Gradle projects are mapped to Eclipse projects to improve how they work with WTP

Jira issues:

- GRADLE-2123
- GRADLE-2396

For Java projects:

All `.classpath` entries should have `nondependency` attribute attached:

       <attributes>
            <attribute name="org.eclipse.jst.component.nondependency" value=""/>
        </attributes>

The `.settings/org.eclipse.wst.common.component` file should list only the source directories and no dependencies.

For War and Ear projects:

All `.classpath` entries should have `nondependency` attribute attached, as above.

The `.settings/org.eclipse.wst.common.component` file should list the runtime dependencies of the project (`runtime - providedRuntime`)

Currently this does not work because `WtpComponentFactory` delegates to `IdeDependenciesExtractor` which resolves to a subset of the dependency graph. Instead,
the entire graph is required.
