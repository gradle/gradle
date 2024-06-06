package org.gradle.client.build.action;

import org.gradle.client.build.model.ResolvedDomPrerequisites;
import org.gradle.declarative.dsl.evaluation.InterpretationSequence;
import org.gradle.declarative.dsl.schema.AnalysisSchema;
import org.gradle.declarative.dsl.tooling.models.DeclarativeSchemaModel;
import org.gradle.internal.Pair;
import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.gradle.GradleBuild;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class GetResolvedDomAction implements BuildAction<ResolvedDomPrerequisites> {

    @Override
    public ResolvedDomPrerequisites execute(BuildController controller) {
        DeclarativeSchemaModel declarativeSchemaModel = controller.getModel(DeclarativeSchemaModel.class);
        InterpretationSequence settingsSchema = declarativeSchemaModel.getSettingsSequence();
        InterpretationSequence projectSchema = declarativeSchemaModel.getProjectSequence();
        Pair<File, List<File>> buildFiles = getDeclarativeBuildFiles(controller);
        return new ResolvedDomPrerequisitesImpl(settingsSchema, projectSchema, buildFiles.getLeft(), buildFiles.getRight());
    }

    private static Pair<File, List<File>> getDeclarativeBuildFiles(BuildController controller) {
        GradleBuild gradleBuild = controller.getModel(GradleBuild.class);
        File rootProjectDirectory = gradleBuild.getRootProject().getProjectDirectory();
        List<File> declarativeBuildFiles = gradleBuild
                .getProjects()
                .getAll()
                .stream()
                .map(p -> new File(p.getProjectDirectory(), "build.gradle.dcl"))
                .filter(File::exists).collect(Collectors.toList());
        if (declarativeBuildFiles.isEmpty()) {
            throw new RuntimeException("No declarative project file found");
        }
        return Pair.of(rootProjectDirectory,  declarativeBuildFiles);
    }

    
    private static final class ResolvedDomPrerequisitesImpl implements ResolvedDomPrerequisites {

        private final InterpretationSequence settingsSequence;
        private final InterpretationSequence projectSequence;
        private final File rootDir;
        private final List<File> declarativeBuildFiles;

        public ResolvedDomPrerequisitesImpl(
                InterpretationSequence settingsSequence,
                InterpretationSequence projectSequence,
                File rootDir, 
                List<File> declarativeBuildFiles
        ) {
            this.settingsSequence = settingsSequence;
            this.projectSequence = projectSequence;
            this.rootDir = rootDir;
            this.declarativeBuildFiles = declarativeBuildFiles;
        }

        @Override
        public InterpretationSequence getSettingsInterpretationSequence() {
            return settingsSequence;
        }

        @Override
        public InterpretationSequence getProjectInterpretationSequence() {
            return projectSequence;
        }

        @Override
        public AnalysisSchema getAnalysisSchema() {
            return StreamSupport.stream(getProjectInterpretationSequence().getSteps().spliterator(), false)
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("no schema step available for project"))
                    .getEvaluationSchemaForStep()
                    .getAnalysisSchema();
        }

        @Override
        public File getRootDir() {
            return rootDir;
        }

        @Override
        public File getSettingsFile() {
            // TODO: this is an assumption about the location of the settings file – get it from Gradle instead.
            return new File(getRootDir(), "settings.gradle.dcl");
        }

        @Override
        public List<File> getDeclarativeBuildFiles() {
            return declarativeBuildFiles;
        }
    }
}