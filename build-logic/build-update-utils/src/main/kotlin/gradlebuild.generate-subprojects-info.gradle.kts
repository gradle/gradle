import gradlebuild.buildutils.tasks.GenerateSubprojectsInfo
import gradlebuild.buildutils.tasks.CheckSubprojectsInfo

tasks.register<GenerateSubprojectsInfo>(GenerateSubprojectsInfo.TASK_NAME)
tasks.register<CheckSubprojectsInfo>("checkSubprojectsInfo")
