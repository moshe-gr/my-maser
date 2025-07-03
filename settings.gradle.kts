dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // jcenter() // Warning: this repository is going to shut down soon - removing deprecated jcenter
    }
}
rootProject.name = "My Maser"
include(":app")