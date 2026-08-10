/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.flow;

import org.gradle.api.Incubating;
import org.gradle.api.configuration.ConfigurationCacheOutcome;
import org.gradle.api.provider.Provider;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

/**
 * Exposes build lifecycle events as {@link Provider providers} so they can be used as inputs
 * to {@link FlowAction dataflow actions}.
 *
 * @since 8.1
 */
@Incubating
@ServiceScope(Scope.Build.class)
public interface FlowProviders {

    /**
     * Returns a {@link Provider provider} for the summary result of the execution of the work scheduled
     * for the current build.
     * <p>
     * The returned {@link Provider#get() provider's value} becomes available after the scheduled work
     * has completed - successfully or otherwise - or after a configuration phase failure prevents execution.
     * </p>
     * <p>
     * <b>IMPORTANT:</b> trying to access the provider's value before the scheduled work has finished will
     * result in an error.
     * </p>
     *
     * <pre>
     * /**
     *  * A settings plugin that plays an appropriate sound at the end of a build.
     *  *{@literal /}
     * class SoundFeedbackPlugin implements Plugin&lt;Settings&gt; {
     *
     *     private final FlowScope flowScope;
     *     private final FlowProviders flowProviders;
     *
     *     {@literal @}Inject
     *     SoundFeedbackPlugin(FlowScope flowScope, FlowProviders flowProviders) {
     *         this.flowScope = flowScope;
     *         this.flowProviders = flowProviders;
     *     }
     *
     *     {@literal @}Override
     *     public void apply(Settings target) {
     *         final File soundsDir = new File(target.getSettingsDir(), "sounds");
     *         flowScope.always(FFPlay.class, spec -&gt;
     *             spec.getParameters().getMediaFile().fileProvider(
     *                 flowProviders.getBuildWorkResult().map(result -&gt;
     *                     new File(
     *                         soundsDir,
     *                         result.getFailure().isPresent() ? "sad-trombone.mp3" : "tada.mp3"
     *                     )
     *                 )
     *             )
     *         );
     *     }
     * }
     * </pre>
     *
     * @see FlowAction
     * @see FlowScope
     * @since 8.1
     */
    Provider<BuildWorkResult> getBuildWorkResult();

    /**
     * Returns a {@link Provider provider} for the outcome of configuration caching for the current
     * build invocation, e.g. whether a configuration cache entry was stored, reused or discarded.
     * <p>
     * The returned {@link Provider#get() provider's value} becomes available after the scheduled work
     * has completed, so it can only be used as an input to {@link FlowAction dataflow actions}.
     * When the configuration cache is not enabled, the value is
     * {@link ConfigurationCacheOutcome.NotEnabled}.
     * </p>
     * <p>
     * There is a single outcome per build invocation: in a composite build, dataflow actions of all
     * builds observe the outcome of the invocation as a whole.
     * </p>
     * <p>
     * <b>IMPORTANT:</b> trying to access the provider's value before the scheduled work has finished
     * will result in an error.
     * </p>
     *
     * <pre class='autoTested'>
     * /**
     *  * Fails the build if the {{@literal @}link Parameters#getOutcome() outcome} is not
     *  * a reused configuration cache entry.
     *  *{@literal /}
     * class VerifyReuse implements FlowAction&lt;VerifyReuse.Parameters&gt; {
     *
     *     interface Parameters extends FlowParameters {
     *         {@literal @}Input
     *         Property&lt;ConfigurationCacheOutcome&gt; getOutcome();
     *     }
     *
     *     {@literal @}Override
     *     public void execute(Parameters parameters) {
     *         ConfigurationCacheOutcome outcome = parameters.getOutcome().get();
     *         if (!ConfigurationCacheOutcome.reused().equals(outcome)) {
     *             throw new GradleException("Expected the configuration cache to be reused, but the outcome was: " + outcome);
     *         }
     *     }
     * }
     *
     * /**
     *  * A settings plugin that verifies that the configuration cache was reused,
     *  * e.g. on the second of two identical CI builds.
     *  *{@literal /}
     * class VerifyConfigurationCacheReusePlugin implements Plugin&lt;Settings&gt; {
     *
     *     private final FlowScope flowScope;
     *     private final FlowProviders flowProviders;
     *
     *     {@literal @}Inject
     *     VerifyConfigurationCacheReusePlugin(FlowScope flowScope, FlowProviders flowProviders) {
     *         this.flowScope = flowScope;
     *         this.flowProviders = flowProviders;
     *     }
     *
     *     {@literal @}Override
     *     public void apply(Settings target) {
     *         flowScope.always(VerifyReuse.class, spec -&gt;
     *             spec.getParameters().getOutcome().set(flowProviders.getConfigurationCacheOutcome())
     *         );
     *     }
     * }
     * </pre>
     *
     * @see FlowAction
     * @see FlowScope
     * @see ConfigurationCacheOutcome
     * @since 9.8.0
     */
    @Incubating
    Provider<ConfigurationCacheOutcome> getConfigurationCacheOutcome();
}
