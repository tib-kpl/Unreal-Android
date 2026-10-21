// Native engine for Unreal Gold 226 data: the Android-ported UE1 v400 engine.
// Packaged into the app next to the v200 engine; the app picks one at launch.
plugins {
    id("com.android.library")
}

android {
    namespace = "com.ast.unreal.engine226"
    compileSdk = 36
    ndkVersion = "23.2.8568313"

    defaultConfig {
        minSdk = 16
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_STL=c++_static")
                cppFlags += listOf("-std=c++17", "-fexceptions", "-frtti", "-fsigned-char")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

// SDL2 sources are downloaded and patched by the app module.
tasks.matching { it.name.startsWith("configureCMake") || it.name.startsWith("buildCMake") }.configureEach {
    dependsOn(":app:prepareSources")
}
