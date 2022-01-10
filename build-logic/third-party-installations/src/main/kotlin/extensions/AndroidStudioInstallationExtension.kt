package extensions

import org.gradle.api.provider.Property

interface AndroidStudioInstallationExtension {

    val autoDownloadAndroidStudio: Property<Boolean>
    val androidStudioVersion: Property<String>
    val runAndroidStudioInHeadlessMode: Property<Boolean>

}
