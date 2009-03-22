/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.api.plugins;

import org.gradle.api.Task;
import org.gradle.api.tasks.ConventionValue;
import org.gradle.api.tasks.bundling.GradleManifest;
import org.gradle.api.tasks.util.FileSet;
import org.gradle.util.GUtil;
import org.gradle.util.WrapUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Hans Dockter
 */
public class DefaultConventionsToPropertiesMapping {

    public final static Map JAVADOC = GUtil.map(
            "srcDirs", new ConventionValue() {
        public Object getValue(Convention convention, Task task) {
            return convention.getPlugin(JavaPluginConvention.class).getSrcDirs();
        }
    },
            "destinationDir", new ConventionValue() {
        public Object getValue(Convention convention, Task task) {
            return convention.getPlugin(JavaPluginConvention.class).getJavadocDir();
        }
    },
            "dependencyManager", new ConventionValue() {
        public Object getValue(Convention convention, Task task) {
            return task.getProject().getDependencies();
        }
    },
            "classesDir", new ConventionValue() {
        public Object getValue(Convention convention, Task task) {
            return convention.getPlugin(JavaPluginConvention.class).getClassesDir();
        }
    });

    public final static Map RESOURCES = GUtil.map(
            "srcDirs", new ConventionValue() {
        public Object getValue(Convention convention, Task task) {
            return convention.getPlugin(JavaPluginConvention.class).getResourceDirs();
        }
    },
            "destinationDir", new ConventionValue() {
        public Object getValue(Convention convention, Task task) {
            return convention.getPlugin(JavaPluginConvention.class).getClassesDir();
        }
    });

    public final static Map COMPILE = GUtil.map(
            "srcDirs", new ConventionValue() {
        public Object getValue(Convention convention, Task task) {
            return convention.getPlugin(JavaPluginConvention.class).getSrcDirs();
        }
    },
            "destinationDir", new ConventionValue() {
        public Object getValue(Convention convention, Task task) {
            return convention.getPlugin(JavaPluginConvention.class).getClassesDir();
        }
    },
            "sourceCompatibility", new ConventionValue() {
        public Object getValue(Convention convention, Task task) {
            return convention.getPlugin(JavaPluginConvention.class).getSourceCompatibility().toString();
        }
    },
            "targetCompatibility", new ConventionValue() {
        public Object getValue(Convention convention, Task task) {
            return convention.getPlugin(JavaPluginConvention.class).getTargetCompatibility().toString();
        }
    },
            "dependencyManager", new ConventionValue() {
        public Object getValue(Convention convention, Task task) {
            return task.getProject().getDependencies();
        }
    });

    public final static Map TEST_RESOURCES = GUtil.map(
            "srcDirs", new ConventionValue() {
        public Object getValue(Convention convention, Task task) {
            return convention.getPlugin(JavaPluginConvention.class).getTestResourceDirs();
        }
    },
            "destinationDir", new ConventionValue() {
        public Object getValue(Convention convention, Task task) {
            return convention.getPlugin(JavaPluginConvention.class).getTestClassesDir();
        }
    });

    public final static Map TEST_COMPILE = GUtil.map(
            "srcDirs", new ConventionValue() {
        public Object getValue(Convention convention, Task task) {
            return convention.getPlugin(JavaPluginConvention.class).getTestSrcDirs();
        }
    },
            "destinationDir", new ConventionValue() {
        public Object getValue(Convention convention, Task task) {
            return convention.getPlugin(JavaPluginConvention.class).getTestClassesDir();
        }
    },
            "sourceCompatibility", new ConventionValue() {
        public Object getValue(Convention convention, Task task) {
            return convention.getPlugin(JavaPluginConvention.class).getSourceCompatibility().toString();
        }
    },
            "targetCompatibility", new ConventionValue() {
        public Object getValue(Convention convention, Task task) {
            return convention.getPlugin(JavaPluginConvention.class).getTargetCompatibility().toString();
        }
    },
            "unmanagedClasspath", new ConventionValue() {
        public Object getValue(Convention convention, Task task) {
            return (WrapUtil.toList(convention.getPlugin(JavaPluginConvention.class).getClassesDir()));
        }
    },
            "dependencyManager", new ConventionValue() {
        public Object getValue(Convention convention, Task task) {
            return task.getProject().getDependencies();
        }
    });

    public final static Map TEST = GUtil.map(
            "testClassesDir", new ConventionValue() {
        public Object getValue(Convention convention, Task task) {
            return convention.getPlugin(JavaPluginConvention.class).getTestClassesDir();
        }
    },
            "testResultsDir", new ConventionValue() {
        public Object getValue(Convention convention, Task task) {
            return convention.getPlugin(JavaPluginConvention.class).getTestResultsDir();
        }
    },
            "testReportDir", new ConventionValue() {
        public Object getValue(Convention convention, Task task) {
            return convention.getPlugin(JavaPluginConvention.class).getTestReportDir();
        }
    },
            "dependencyManager", new ConventionValue() {
        public Object getValue(Convention convention, Task task) {
            return task.getProject().getDependencies();
        }
    },
			"testSrcDirs", new ConventionValue() {
        public Object getValue(Convention convention, Task task) {
            // TODO IMPROVE I really don't like this but don't know a better way at the moment
            if ( task.getProject().getAppliedPlugins().contains(GroovyPlugin.class) ) {
                return convention.getPlugin(GroovyPluginConvention.class).getGroovyTestSrcDirs();
            }
            else {
                return convention.getPlugin(JavaPluginConvention.class).getTestSrcDirs();
            }
        }
    });

    public final static Map ARCHIVE = GUtil.map(
            "version", new ConventionValue() {
        public Object getValue(Convention convention, Task task) {
            return "" + task.getProject().property("version");
        }
        }, "baseName", new ConventionValue() {
        public Object getValue(Convention convention, Task task) {
            return "" + task.getProject().property("archivesBaseName");
        }
    });

    public final static Map ZIP = new HashMap(ARCHIVE);

    public final static Map TAR = new HashMap(ZIP);

    public final static Map JAR = new HashMap(ARCHIVE);

    static {
        JAR.putAll(GUtil.map(
                "baseDir", new ConventionValue() {
            public Object getValue(Convention convention, Task task) {
                return convention.getPlugin(JavaPluginConvention.class).getClassesDir();
            }
        }, "manifest", new ConventionValue() {
            public Object getValue(Convention convention, Task task) {
                return new GradleManifest(convention.getPlugin(JavaPluginConvention.class).getManifest().getManifest());
            }
        },
                "metaInfResourceCollections", new ConventionValue() {
            public Object getValue(Convention convention, Task task) {
                return convention.getPlugin(JavaPluginConvention.class).getMetaInf();
            }
        }));
    }


    public final static Map WAR = new HashMap(JAR);

    static {
        WAR.remove("baseDir");
        WAR.putAll(GUtil.map(
                "classesFileSets", new ConventionValue() {
            public Object getValue(Convention convention, Task task) {
                return WrapUtil.toList(new FileSet(convention.getPlugin(JavaPluginConvention.class).getClassesDir()));
            }
        },      "resourceCollections", new ConventionValue() {
            public Object getValue(Convention convention, Task task) {
                return WrapUtil.toList(new FileSet(convention.getPlugin(JavaPluginConvention.class).getWebAppDir()));
            }
        },
                "libExcludeConfigurations", new ConventionValue() {
            public Object getValue(Convention convention, Task task) {
                return WrapUtil.toList(WarPlugin.PROVIDED_RUNTIME);
            }
        },
                "libConfigurations", new ConventionValue() {
            public Object getValue(Convention convention, Task task) {
                return WrapUtil.toList(JavaPlugin.RUNTIME);
            }
        }));
    }

    public final static Map<String, ConventionValue> LIB = GUtil.map(
            "defaultArchiveTypes", new ConventionValue() {
        public Object getValue(Convention convention, Task task) {
            return convention.getPlugin(JavaPluginConvention.class).getArchiveTypes();
        }
    });

    public final static Map<String, ConventionValue> DIST = GUtil.map(
            "defaultArchiveTypes", new ConventionValue() {
        public Object getValue(Convention convention, Task task) {
            return convention.getPlugin(JavaPluginConvention.class).getArchiveTypes();
        }
    });


}
