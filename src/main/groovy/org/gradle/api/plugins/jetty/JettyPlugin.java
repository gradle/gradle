/*
 * Copyright 2007-2008 the original author or authors.
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

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Action;
import org.gradle.api.internal.IConventionAware;
import org.gradle.api.internal.project.PluginRegistry;
import org.gradle.api.plugins.*;
import org.gradle.api.tasks.ConventionValue;
import org.gradle.api.tasks.bundling.War;

import java.io.File;
import java.util.Map;

/**
 * <p>A {@link Plugin} which extends the {@link WarPlugin} to add tasks which run the web application under Jetty.</p>
 * 
 * @author Hans Dockter
 */
public class JettyPlugin implements Plugin {
    public static final String JETTY_RUN = "jettyRun";
    public static final String JETTY_RUN_WAR = "jettyRunWar";
    public static final String JETTY_RUN_EXPLODED_WAR = "jettyRunExploded";
    public static final String JETTY_STOP = "jettyStop";

    public static final String RELOAD_AUTOMATIC = "automatic";
    public static final String RELOAD_MANUAL = "manual";

    public void apply(Project project, PluginRegistry pluginRegistry, Map<String, ?> customValues) {
        pluginRegistry.apply(WarPlugin.class, project, customValues);
        JettyPluginConvention jettyConvention = new JettyPluginConvention();
        Convention convention = project.getConvention();
        convention.getPlugins().put("jetty", jettyConvention);

        configureMappingRules(project, jettyConvention);
        configureJettyRun(project);
        configureJettyRunWar(project);
        configureJettyRunWarExploded(project);
        configureJettyStop(project, jettyConvention);
    }

    private void configureMappingRules(final Project project, final JettyPluginConvention jettyConvention) {
        project.getTasks().withType(AbstractJettyRunTask.class).whenTaskAdded(new Action<AbstractJettyRunTask>() {
            public void execute(AbstractJettyRunTask abstractJettyRunTask) {
                configureAbstractJettyTask(project, jettyConvention, abstractJettyRunTask);
            }
        });
    }

    private void configureJettyRunWarExploded(Project project) {
        JettyRunWarExploded jettyRunWarExploded = project.getTasks().add(JETTY_RUN_EXPLODED_WAR, JettyRunWarExploded.class);
        jettyRunWarExploded.setDescription("Assembles the webapp into an exploded war and deploys it to Jetty.");
    }

    private void configureJettyRunWar(final Project project) {
        project.getTasks().withType(JettyRunWar.class).whenTaskAdded(new Action<JettyRunWar>() {
            public void execute(JettyRunWar jettyRunWar) {
                jettyRunWar.dependsOn(WarPlugin.WAR_TASK_NAME);
                jettyRunWar.getConventionMapping().put("webApp", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return ((War) project.task(WarPlugin.WAR_TASK_NAME)).getArchivePath();
                    }
                });
            }
        });

        JettyRunWar jettyRunWar = project.getTasks().add(JETTY_RUN_WAR, JettyRunWar.class);
        jettyRunWar.setDescription("Assembles the webapp into a war and deploys it to Jetty.");
    }

    private void configureJettyStop(Project project, final JettyPluginConvention jettyConvention) {
        JettyStop jettyStop = project.getTasks().add(JETTY_STOP, JettyStop.class);
        jettyStop.setDescription("Stops Jetty.");
        jettyStop.getConventionMapping().put("stopPort", new ConventionValue() {
            public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                return jettyConvention.getStopPort();
            }
        });
        jettyStop.getConventionMapping().put("stopKey", new ConventionValue() {
            public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                return jettyConvention.getStopKey();
            }
        });
    }

    private void configureJettyRun(final Project project) {
        project.getTasks().withType(JettyRun.class).whenTaskAdded(new Action<JettyRun>() {
            public void execute(JettyRun jettyRun) {
                jettyRun.dependsOn(JavaPlugin.COMPILE_TESTS_TASK_NAME);
                jettyRun.setConfiguration(JavaPlugin.RUNTIME_CONFIGURATION_NAME);
                jettyRun.setTestConfiguration(JavaPlugin.TEST_RUNTIME_CONFIGURATION_NAME);
                jettyRun.setUseTestClasspath(false);
                jettyRun.getConventionMapping().put("webXml", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return getWebXml(project);
                    }
                });
                jettyRun.getConventionMapping().put("classesDirectory", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return getJavaConvention(project).getClassesDir();
                    }
                });
                jettyRun.getConventionMapping().put("testClassesDirectory", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return getJavaConvention(project).getTestClassesDir();
                    }
                });
                jettyRun.getConventionMapping().put("webAppSourceDirectory", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return getWarConvention(project).getWebAppDir();
                    }
                });
            }
        });

        JettyRun jettyRun = project.getTasks().add(JETTY_RUN, JettyRun.class);
        jettyRun.setDescription("Uses your files as and where they are and deploys them to Jetty.");
    }

    private Object getWebXml(Project project) {
        War war = (War) project.task(WarPlugin.WAR_TASK_NAME);
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
        jettyTask.getConventionMapping().put("contextPath", new ConventionValue() {
            public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                return ((War) project.task(WarPlugin.WAR_TASK_NAME)).getBaseName();
            }
        });
        jettyTask.getConventionMapping().put("tmpDirectory", new ConventionValue() {
            public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                return new File(project.getBuildDir(), "jetty");
            }
        });
        jettyTask.getConventionMapping().put("httpPort", new ConventionValue() {
            public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                return jettyConvention.getHttpPort();
            }
        });
        jettyTask.getConventionMapping().put("stopPort", new ConventionValue() {
            public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                return jettyConvention.getStopPort();
            }
        });
        jettyTask.getConventionMapping().put("stopKey", new ConventionValue() {
            public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
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
