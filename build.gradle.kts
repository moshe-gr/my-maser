plugins {
    id("com.android.application") version "9.5.1" apply false
    id("org.jetbrains.kotlin.android") version "2.4.21" apply false
    id("com.google.devtools.ksp") version "2.3.12" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}

tasks.register<Delete>("clean") {
    description = ""
    delete(layout.buildDirectory.asFile)
}