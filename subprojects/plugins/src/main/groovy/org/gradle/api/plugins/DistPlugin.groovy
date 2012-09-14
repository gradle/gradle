package org.gradle.api.plugins

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.CopySpec;
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.bundling.Tar

abstract class DistPlugin implements Plugin<Project>{

	static final String TASK_DIST_ZIP_NAME = "distZip"
	static final String TASK_DIST_TAR_NAME = "distTar"
	
	protected Project project
	
	void apply(final Project project) {
		this.project = project
		project.plugins.apply(JavaPlugin)

		addPluginConvention()
		configureDistSpec(getDistribution())
		addDistZipTask()
		addDistTarTask()
	}
	
	protected abstract String getGroup()
	
	protected abstract void addPluginConvention()
	
	protected abstract CopySpec getDistribution()
	
	protected abstract CopySpec configureDistSpec(CopySpec distribution)
	
	protected abstract String getArtefactName() 
	
	protected void addDistZipTask() {
		def distZipTask = project.tasks.add(TASK_DIST_ZIP_NAME, Zip)
		distZipTask.description = "Bundles the project artefact and dependencies as a zip."
		distZipTask.group = getGroup()
		distZipTask.conventionMapping.baseName = { getArtefactName() }
		def baseDir = { distZipTask.archiveName - ".zip" }
		distZipTask.into(baseDir) {
			with(getDistribution())
		}
	}
	
	protected void addDistTarTask() {
		def distTarTask = project.tasks.add(TASK_DIST_TAR_NAME, Tar)
		distTarTask.description = "Bundles the project artefact and dependencies as a tar."
		distTarTask.group = getGroup()
		distTarTask.conventionMapping.baseName = { getArtefactName() }
		def baseDir = { distTarTask.archiveName - ".tar" }
		distTarTask.into(baseDir) {
			with(getDistribution())
		}
	}

}
