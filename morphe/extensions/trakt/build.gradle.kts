import com.android.build.api.dsl.ApplicationExtension

dependencies {
    compileOnly(libs.morphe.extensions.library)
    compileOnly("androidx.preference:preference:1.2.1")
}

configure<ApplicationExtension> {
    compileSdk = 36

    defaultConfig {
        minSdk = 23
    }

    // The Gadget JSON config is deliberately named *.so so Frida resolves it
    // next to libfrida-gadget.so. Legacy packaging extracts both files.
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}
