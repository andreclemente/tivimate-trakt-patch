group = "com.tivimate.traktpatch"

patches {
    about {
        name = "TiviMate Trakt Morphe Patches"
        description = "Morphe patch bundle for TiviMate Trakt runtime sync research; v0.1.5 contains an opt-in x86_64 Android TV diagnostic patch only"
        source = "https://github.com/andreclemente/tivimate-trakt-patch"
        author = "André Clemente"
        contact = "https://github.com/andreclemente/tivimate-trakt-patch/issues"
        website = "https://github.com/andreclemente/tivimate-trakt-patch"
        license = "GNU General Public License v3.0, with additional GPL section 7 requirements"
    }
}

dependencies {
    implementation(libs.guava)
    implementation(libs.morphe.patches.library)
    compileOnly(project(":patches:stub"))
}

kotlin {
    compilerOptions {
        freeCompilerArgs = listOf("-Xcontext-parameters")
    }
}
