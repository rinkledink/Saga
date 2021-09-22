package io.github.nomisrev

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.UnknownTaskException
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.gradle.plugins.signing.SigningExtension

class MppPublishingPlugin : Plugin<Project> {
  override fun apply(target: Project) {}
}

fun Project.setupPublishing(
  pomDevId: String,
  pomDevName: String,
  projectUrl: String,
  projectDesc: String,
  releaseRepo: java.net.URI,
  snapshotRepo: java.net.URI,
  projectGitUrl: String = "$projectUrl.git",
  key: String? = System.getenv("SIGNINGKEY"),
  pass: String? = System.getenv("SIGNINGPASSWORD"),
) {
  afterEvaluate {
    extensions.getByType(PublishingExtension::class.java).apply {
      val mavenPublications = publications.withType<MavenPublication>()
      mavenPublications.all {
        artifact(project.javadocJar())
        setupPom(
          gitUrl = projectGitUrl,
          url = projectUrl,
          description = projectDesc,
          pomDevId = pomDevId,
          pomDevName = pomDevName
        )
      }
      repositories {
        maven(if (version.toString().endsWith("SNAPSHOT")) snapshotRepo else releaseRepo)
      }
      Nullable.zip(key, pass) { key, pass -> signPublications(key, pass) }
    }
  }
}

object Nullable {
  fun <A : Any, B : Any, C : Any> zip(a: A?, b: B?, f: (A, B) -> C?): C? =
    a?.let { aa ->
      b?.let { bb -> f(aa, bb) }
    }
}

fun Project.signPublications(
  key: String,
  pass: String
) {
  val publications = project
    .extensions
    .getByType(PublishingExtension::class.java)
    .publications

  project
    .extensions
    .getByType(SigningExtension::class.java)
    .apply {
      useInMemoryPgpKeys(key, pass)
    }.sign(publications)
}

fun Project.javadocJar(): TaskProvider<Jar> {
  val taskName = "javadocJar"
  return try {
    tasks.named(name = taskName)
  } catch (e: UnknownTaskException) {
    tasks.register(name = taskName) { archiveClassifier.set("javadoc") }
  }
}

fun MavenPublication.setupPom(
  gitUrl: String,
  url: String,
  description: String,
  pomDevId: String,
  pomDevName: String,
  licenseName: String = "The Apache Software License, Version 2.0",
  licenseUrl: String = "https://www.apache.org/licenses/LICENSE-2.0.txt"
) {
  pom {
    if (!name.isPresent) {
      name.set(artifactId)
    }
    this@pom.description.set(description)
    this@pom.url.set(url)
    licenses {
      license {
        name.set(licenseName)
        this@license.url.set(licenseUrl)
      }
    }
    developers {
      developer {
        id.set(pomDevId)
        name.set(pomDevName)
      }
    }
    scm {
      connection.set(gitUrl)
      developerConnection.set(gitUrl)
      this@scm.url.set(url)
    }
    if (gitUrl.startsWith("https://github.com")) issueManagement {
      system.set("GitHub")
      this@issueManagement.url.set(gitUrl.replace(".git", "/issues"))
    }
  }
}

fun RepositoryHandler.maven(
  uri: java.net.URI,
  sonatypeUsername: String? = System.getenv("SONATYPE_USER"),
  sonatypePassword: String? = System.getenv("SONATYPE_PWD"),
): MavenArtifactRepository = maven {
  name = "Maven"
  url = uri
  credentials {
    username = sonatypeUsername
    password = sonatypePassword
  }
}