package org.gradle.client.build.action;

import org.gradle.client.build.model.ResolvedDomPrerequisites;
import org.gradle.declarative.dsl.schema.AnalysisSchema;
import org.gradle.declarative.dsl.tooling.models.DeclarativeSchemaModel;
import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.gradle.GradleBuild;

import java.io.File;

public class GetResolvedDomAction implements BuildAction<ResolvedDomPrerequisites> {

    @Override
    public ResolvedDomPrerequisites execute(BuildController controller) {
        AnalysisSchema projectSchema = getProjectSchema(controller);
        String buildFileContent = getBuildFileContent(controller);
        return new ResolvedDomPrerequisitesImpl(projectSchema, buildFileContent);
    }

    private static AnalysisSchema getProjectSchema(BuildController controller) {
        DeclarativeSchemaModel declarativeSchemaModel = controller.getModel(DeclarativeSchemaModel.class);
        return declarativeSchemaModel.getProjectSchema();
    }

    private static String getBuildFileContent(BuildController controller) {
        GradleBuild gradleBuild = controller.getModel(GradleBuild.class);
        File randomProjectBuildFile = gradleBuild.getProjects().getAll().stream()
                .map(p -> new File(p.getProjectDirectory(), "build.gradle.dcl"))
                .filter(File::exists)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Declarative project file not found"));

        return randomProjectBuildFile.getAbsolutePath();


    }

    private static final class ResolvedDomPrerequisitesImpl implements ResolvedDomPrerequisites {

        private final AnalysisSchema analysisSchema;
        private final String buildFilePath;

        public ResolvedDomPrerequisitesImpl(AnalysisSchema analysisSchema, String buildFilePath) {
            this.analysisSchema = analysisSchema;
            this.buildFilePath = buildFilePath;
        }

        @Override
        public AnalysisSchema getAnalysisSchema() {
            return analysisSchema;
        }

        @Override
        public String getBuildFilePath() {
            return buildFilePath;
        }
    }
}