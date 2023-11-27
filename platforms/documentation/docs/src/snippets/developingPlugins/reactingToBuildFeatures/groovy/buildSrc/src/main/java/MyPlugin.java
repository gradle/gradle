import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.configuration.BuildFeatures;

import javax.inject.Inject;

// tag::my-plugin[]
public abstract class MyPlugin implements Plugin<Project> {

    @Inject
    protected abstract BuildFeatures getBuildFeatures(); // <1>

    @Override
    public void apply(Project p) {
        BuildFeatures buildFeatures = getBuildFeatures();

        Boolean configCacheRequested = buildFeatures.getConfigurationCache().getRequested() // <2>
            .getOrNull(); // could be null if user did not opt in nor opt out
        String configCacheUsage = describeFeatureUsage(configCacheRequested);
        MyReport myReport = new MyReport();
        myReport.setConfigurationCacheUsage(configCacheUsage);

        boolean isolatedProjectsActive = buildFeatures.getIsolatedProjects().getActive() // <3>
            .get(); // the active state is always defined
        if (!isolatedProjectsActive) {
            myOptionalPluginLogicIncompatibleWithIsolatedProjects();
        }
    }

    private String describeFeatureUsage(Boolean requested) {
        return requested == null ? "no preference" : requested ? "opt-in" : "opt-out";
    }

    private void myOptionalPluginLogicIncompatibleWithIsolatedProjects() {
    }
}
// end::my-plugin[]

class MyReport {
    public void setConfigurationCacheUsage(String usage) {
    }
}
