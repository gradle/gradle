/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.plugins.jetty;

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.Convention;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.plugins.WarPlugin;
import org.gradle.api.plugins.WarPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.bundling.War;
import org.gradle.util.DeprecationLogger;

import java.io.File;
import java.util.concurrent.Callable;

/**
 * <p>A {@link Plugin} which extends the {@link WarPlugin} to add tasks which run the web application using an embedded
 * Jetty web container.</p>
 *
 * @deprecated The Jetty plugin has been deprecated
 */
@Deprecated
public class JettyPlugin implements Plugin<Project> {
    public static final String JETTY_RUN = "jettyRun";
    public static final String JETTY_RUN_WAR = "jettyRunWar";
    public static final String JETTY_STOP = "jettyStop";

    public static final String RELOAD_AUTOMATIC = "automatic";
    public static final String RELOAD_MANUAL = "manual";

    public void apply(Project project) {
        DeprecationLogger.nagUserOfPluginReplacedWithExternalOne("Jetty", "Gretty (https://github.com/akhikhl/gretty)");
        project.getPluginManager().apply(WarPlugin.class);
        JettyPluginConvention jettyConvention = new JettyPluginConvention();
        Convention convention = project.getConvention();
        convention.getPlugins().put("jetty", jettyConvention);

        configureMappingRules(project, jettyConvention);
        configureJettyRun(project);
        configureJettyRunWar(project);
        configureJettyStop(project, jettyConvention);
    }

    private void configureMappingRules(final Project project, final JettyPluginConvention jettyConvention) {
        project.getTasks().withType(AbstractJettyRunTask.class, new Action<AbstractJettyRunTask>() {
            public void execute(AbstractJettyRunTask abstractJettyRunTask) {
                configureAbstractJettyTask(project, jettyConvention, abstractJettyRunTask);
            }
        });
    }

    private void configureJettyRunWar(final Project project) {
        project.getTasks().withType(JettyRunWar.class, new Action<JettyRunWar>() {
            public void execute(JettyRunWar jettyRunWar) {
                jettyRunWar.dependsOn(WarPlugin.WAR_TASK_NAME);
                jettyRunWar.getConventionMapping().map("webApp", new Callable<Object>() {
                    public Object call() throws Exception {
                        return ((War) project.getTasks().getByName(WarPlugin.WAR_TASK_NAME)).getArchivePath();
                    }
                });
            }
        });

        JettyRunWar jettyRunWar = project.getTasks().create(JETTY_RUN_WAR, JettyRunWar.class);
        jettyRunWar.setDescription("Assembles the webapp into a war and deploys it to Jetty.");
        jettyRunWar.setGroup(WarPlugin.WEB_APP_GROUP);
    }

    private void configureJettyStop(Project project, final JettyPluginConvention jettyConvention) {
        JettyStop jettyStop = project.getTasks().create(JETTY_STOP, JettyStop.class);
        jettyStop.setDescription("Stops Jetty.");
        jettyStop.setGroup(WarPlugin.WEB_APP_GROUP);
        jettyStop.getConventionMapping().map("stopPort", new Callable<Object>() {
            public Object call() throws Exception {
                return jettyConvention.getStopPort();
            }
        });
        jettyStop.getConventionMapping().map("stopKey", new Callable<Object>() {
            public Object call() throws Exception {
                return jettyConvention.getStopKey();
            }
        });
    }

    private void configureJettyRun(final Project project) {
        project.getTasks().withType(JettyRun.class, new Action<JettyRun>() {
            public void execute(JettyRun jettyRun) {
                jettyRun.getConventionMapping().map("webXml", new Callable<Object>() {
                    public Object call() throws Exception {
                        return getWebXml(project);
                    }
                });
                jettyRun.getConventionMapping().map("classpath", new Callable<Object>() {
                    public Object call() throws Exception {
                        return getJavaConvention(project).getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME).getRuntimeClasspath();
                    }
                });
                jettyRun.getConventionMapping().map("webAppSourceDirectory", new Callable<Object>() {
                    public Object call() throws Exception {
                        return getWarConvention(project).getWebAppDir();
                    }
                });
            }
        });

        JettyRun jettyRun = project.getTasks().create(JETTY_RUN, JettyRun.class);
        jettyRun.setDescription("Uses your files as and where they are and deploys them to Jetty.");
        jettyRun.setGroup(WarPlugin.WEB_APP_GROUP);
    }

    private Object getWebXml(Project project) {
        War war = (War) project.getTasks().getByName(WarPlugin.WAR_TASK_NAME);
        File webXml;
        if (war.getWebXml() != null) {
            webXml = war.getWebXml();
        } else {
            webXml = new File(getWarConvention(project).getWebAppDir(), "WEB-INF/web.xml");
        }
        return webXml;
    }

    private void configureAbstractJettyTask(final Project project, final JettyPluginConvention jettyConvention, AbstractJettyRunTask jettyTask) {
        jettyTask.setDaemon(false);
        jettyTask.setReload(RELOAD_AUTOMATIC);
        jettyTask.setScanIntervalSeconds(0);
        jettyTask.getConventionMapping().map("contextPath", new Callable<Object>() {
            public Object call() throws Exception {
                return ((War) project.getTasks().getByName(WarPlugin.WAR_TASK_NAME)).getBaseName();
            }
        });
        jettyTask.getConventionMapping().map("httpPort", new Callable<Object>() {
            public Object call() throws Exception {
                return jettyConvention.getHttpPort();
            }
        });
        jettyTask.getConventionMapping().map("stopPort", new Callable<Object>() {
            public Object call() throws Exception {
                return jettyConvention.getStopPort();
            }
        });
        jettyTask.getConventionMapping().map("stopKey", new Callable<Object>() {
            public Object call() throws Exception {
                return jettyConvention.getStopKey();
            }
        });
    }

    public JavaPluginConvention getJavaConvention(Project project) {
        return project.getConvention().getPlugin(JavaPluginConvention.class);
    }

    public WarPluginConvention getWarConvention(Project project) {
        return project.getConvention().getPlugin(WarPluginConvention.class);
    }
}
