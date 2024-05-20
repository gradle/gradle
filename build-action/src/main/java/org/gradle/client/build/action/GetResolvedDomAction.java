package org.gradle.client.build.action;

import org.gradle.client.build.model.ResolvedDomPrerequisites;
import org.gradle.declarative.dsl.schema.AnalysisSchema;
import org.gradle.declarative.dsl.tooling.models.DeclarativeSchemaModel;
import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.gradle.GradleBuild;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

public class GetResolvedDomAction implements BuildAction<ResolvedDomPrerequisites> {

    @Override
    public ResolvedDomPrerequisites execute(BuildController controller) {
        AnalysisSchema projectSchema = getProjectSchema(controller);
        List<File> declarativeBuildFiles = getDeclarativeBuildFiles(controller);
        return new ResolvedDomPrerequisitesImpl(projectSchema, declarativeBuildFiles);
    }

    private static AnalysisSchema getProjectSchema(BuildController controller) {
        DeclarativeSchemaModel declarativeSchemaModel = controller.getModel(DeclarativeSchemaModel.class);
        return declarativeSchemaModel.getProjectSchema();
    }

    private static List<File> getDeclarativeBuildFiles(BuildController controller) {
        GradleBuild gradleBuild = controller.getModel(GradleBuild.class);
        List<File> declarativeBuildFiles = gradleBuild
                .getProjects()
                .getAll()
                .stream()
                .map(p -> new File(p.getProjectDirectory(), "build.gradle.dcl"))
                .filter(File::exists).collect(Collectors.toList());
        if (declarativeBuildFiles.isEmpty()) {
            throw new RuntimeException("Declarative project file not found");
        }
        return declarativeBuildFiles;
    }

    private static final class ResolvedDomPrerequisitesImpl implements ResolvedDomPrerequisites {

        private final AnalysisSchema analysisSchema;
        private final List<File> declarativeBuildFiles;

        public ResolvedDomPrerequisitesImpl(AnalysisSchema analysisSchema, List<File> declarativeBuildFiles) {
            this.analysisSchema = analysisSchema;
            this.declarativeBuildFiles = declarativeBuildFiles;
        }

        @Override
        public AnalysisSchema getAnalysisSchema() {
            return analysisSchema;
        }


        @Override
        public List<File> getDeclarativeBuildFiles() {
            return declarativeBuildFiles;
        }
    }
}