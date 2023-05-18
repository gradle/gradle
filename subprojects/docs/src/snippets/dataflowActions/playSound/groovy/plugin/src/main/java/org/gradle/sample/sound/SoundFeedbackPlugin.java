// tag::flow-action[]
package org.gradle.sample.sound;

import org.gradle.api.Plugin;
import org.gradle.api.flow.FlowProviders;
import org.gradle.api.flow.FlowScope;
import org.gradle.api.initialization.Settings;

import javax.inject.Inject;
import java.io.File;

public abstract class SoundFeedbackPlugin implements Plugin<Settings> {
    @Inject
    protected abstract FlowScope getFlowScope(); // <1>

    @Inject
    protected abstract FlowProviders getFlowProviders(); // <1>

    @Override
    public void apply(Settings settings) {
// end::flow-action[]
        settings.getGradle().getSharedServices().registerIfAbsent("soundService", SoundService.class, spec -> {});

// tag::flow-action[]
        final File soundsDir = new File(settings.getSettingsDir(), "sounds");
        getFlowScope().always( // <2>
            SoundPlay.class,  // <3>
            spec ->  // <4>
                spec.getParameters().getMediaFile().set(
                    getFlowProviders().getBuildWorkResult().map(result -> // <5>
                        new File(
                            soundsDir,
                            result.getFailure().isPresent() ? "sad-trombone.mp3" : "tada.mp3"
                        )
                    )
                )
        );
    }
}
// end::flow-action[]
