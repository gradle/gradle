package org.gradle.api.plugins

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.bundling.Zip;

/**
 * A {@link Plugin} which package project as a distribution including
 *  JAR, API documentation and source JAR for the project.
 * @author scogneau
 *
 */
class JvmLibraryPlugin implements Plugin<Project>{
	static final String JAVA_LIBRARY_PLUGIN_NAME = "jvm-library"
	static final String JAVA_LIBRARY_GROUP = JAVA_LIBRARY_PLUGIN_NAME
	static final String TASK_DIST_ZIP_NAME = "distZip"
	
	private DistributionExtension extension
	private Project project
	
	public void apply(Project project) {
		this.project = project
		project.plugins.apply(JavaPlugin)
		
		addPluginExtension()
		addDistZipTask()
	}

	private void addPluginExtension(){
		extension = new DistributionExtension()
		extension.name = project.name
		project.extensions.add("distribution", extension)
	}
	
	
	private void addDistZipTask(){
		def distZipTask = project.tasks.add(TASK_DIST_ZIP_NAME, Zip)
		distZipTask.description = "Bundles the project as a jvm library."
		distZipTask.group = JAVA_LIBRARY_GROUP
		distZipTask.conventionMapping.baseName = {extension.name }
		def baseDir = { distZipTask.archiveName - ".zip" }
		def jar = project.tasks[JavaPlugin.JAR_TASK_NAME]
		distZipTask.with{
			from(jar)
			from(project.file("src/dist"))
			into("lib") {
				from(project.configurations.runtime)
			}
		}
	}
	
}
