import java.util.Properties
import java.io.File

plugins { id("com.android.application") }
android {
    namespace = "fr.hugoviaud.zwack"
    compileSdk = 36
    bundle { language { enableSplit = false } }
    defaultConfig {
        applicationId = "fr.hugoviaud.cadensio"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }
    val signingPath = System.getenv("CADENSIO_SIGNING_PROPERTIES")
    if (signingPath != null) {
        val signing = Properties().apply { File(signingPath).inputStream().use { load(it) } }
        signingConfigs {
            create("release") {
                storeFile = file(signing.getProperty("storeFile"))
                storePassword = signing.getProperty("storePassword")
                keyAlias = signing.getProperty("keyAlias")
                keyPassword = signing.getProperty("keyPassword")
            }
        }
        buildTypes.getByName("release").signingConfig = signingConfigs.getByName("release")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
