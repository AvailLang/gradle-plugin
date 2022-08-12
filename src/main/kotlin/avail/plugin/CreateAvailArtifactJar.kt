/*
 * CreateDigestsFileTask.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * * Neither the name of the copyright holder nor the names of the contributors
 *   may be used to endorse or promote products derived from this software
 *   without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package avail.plugin

import org.availlang.artifact.ArtifactDescriptor
import org.availlang.artifact.AvailArtifactType
import org.gradle.api.tasks.*
import org.availlang.artifact.manifest.AvailArtifactManifest
import org.availlang.artifact.manifest.AvailManifestRoot
import org.availlang.artifact.AvailArtifact
import org.availlang.artifact.AvailRootArtifactTarget
import org.availlang.artifact.jar.AvailArtifactJar
import org.availlang.artifact.jar.AvailArtifactJarBuilder
import org.availlang.artifact.jar.JvmComponent
import org.gradle.jvm.tasks.Jar
import java.io.File
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import java.util.jar.Attributes
import java.util.jar.JarFile
import java.util.zip.ZipFile

/**
 * Perform all tasks necessary to package the Avail Standard Library as an
 * [AvailArtifact].
 *
 * This performs the following tasks:
 * 1. Creates the [ArtifactDescriptor] file.
 * 2. Creates the [AvailArtifactManifest] file.
 * 3. Creates source digests file.
 *
 * @author Richard Arriaga
 */
open class CreateAvailArtifactJar : Jar()
{
	/**
	 * The name of the [Configuration] that the dependencies are added to.
	 */
	private val configName = "_createAvailArtifact$name"

	/**
	 * The [Configuration] for adding [dependencies][Dependency] to be included
	 * in the workbench jar.
	 */
	private val localConfig: Configuration by lazy {
		project.configurations.create(configName)
	}

	/**
	 * The [AvailArtifactType] of the [AvailArtifact] to create.
	 */
	@Input
	var artifactType: AvailArtifactType = AvailArtifactType.LIBRARY

	/**
	 * The [JvmComponent] that describes any JVM components being packaged in
	 * the artifact or [JvmComponent.NONE] if none.
	 */
	@Input
	var jvmComponent: JvmComponent = JvmComponent.NONE

	/**
	 * The description of the [AvailArtifact] used in the
	 * [AvailArtifactManifest].
	 */
	@Input
	var artifactDescription: String = ""

	/**
	 * The title of the artifact being created that will be added to the jar
	 * manifest ([Attributes.Name.IMPLEMENTATION_TITLE]).
	 */
	@Input
	var implementationTitle: String = project.name

	/**
	 * The absolute path to the location where the jar file is to be written.
	 *
	 * It is set to the following by default:
	 * ```
	 * "${project.buildDir}/libs/${project.name}-${project.version}.jar"
	 * ```
	 */
	@Input
	var outputJarTargetFile =
		"${project.buildDir}/libs/${project.name}-${project.version}.jar"

	/**
	 * The list of [AvailRootArtifactTarget]s to add to the artifact jar.
	 */
	private val roots = mutableListOf<AvailRootArtifactTarget>()

	/**
	 * Add the [AvailRootArtifactTarget] to be included in the artifact jar.
	 *
	 * @param root
	 *   The [AvailRootArtifactTarget].
	 */
	@Suppress("unused")
	fun addRoot (root: AvailRootArtifactTarget)
	{
		roots.add(root)
	}

	/**
	 * The list of [File] - target directory inside artifact for it to be placed
	 * [Pair]s.
	 */
	private val includedFiles = mutableListOf<Pair<File, String>>()

	/**
	 * Add a singular [File] to be written in the specified target directory
	 * path inside the jar.
	 *
	 * @param file
	 *   The [File] to write. Note this must not be a directory.
	 * @param targetDirectory
	 *   The path relative directory where the file should be placed inside the
	 *   jar file.
	 */
	@Suppress("unused")
	fun addFile (file: File, targetDirectory: String)
	{
		require(!file.isDirectory)
		{
			"Expected $file to be a file not a directory!"
		}

		includedFiles.add(file to targetDirectory)
	}

	/**
	 * The list of [JarFile]s to add to the artifact jar.
	 */
	private val jars = mutableListOf<JarFile>()

	/**
	 * Add the [JarFile] to be included in the artifact jar.
	 *
	 * @param jar
	 *   The [JarFile].
	 */
	@Suppress("unused")
	fun addJar (jar: JarFile)
	{
		jars.add(jar)
	}

	/**
	 * The list of [ZipFile]s to add to the artifact jar.
	 */
	private val zipFiles = mutableListOf<ZipFile>()

	/**
	 * Add the [ZipFile] to be included in the artifact jar.
	 *
	 * @param zipFile
	 *   The [ZipFile].
	 */
	@Suppress("unused")
	fun addZipFile (zipFile: ZipFile)
	{
		zipFiles.add(zipFile)
	}

	/**
	 * The list of [File] directories whose contents should be added to
	 * the artifact jar.
	 */
	private val directories = mutableListOf<File>()

	/**
	 * Add the [directory][File] to be included in the artifact jar. This must
	 * be a directory: [File.isDirectory].
	 *
	 * @param file
	 *   The [File] directory to add.
	 */
	@Suppress("unused")
	fun addDirectory (file: File)
	{
		directories.add(file)
	}

	/**
	 * Add a dependency to be included in the jar.
	 *
	 * @param dependency
	 *   The string that identifies the dependency such as:
	 *   `org.package:myLibrary:2.3.1`.
	 */
	@Suppress("unused")
	fun dependency (dependency: String)
	{
		localConfig.dependencies.add(project.dependencies.create(dependency))
	}

	/**
	 * Add a dependency to be included in the jar.
	 *
	 * @param dependency
	 *   The [Dependency] to add.
	 */
	@Suppress("unused")
	fun dependency (dependency: Dependency)
	{
		localConfig.dependencies.add(dependency)
	}

	init
	{
		group = "build"
		description = "Create an Avail artifact jar."
	}

	/**
	 * Construct the [AvailArtifactJar].
	 */
	@TaskAction
	fun createAvailArtifactJar ()
	{
		createAvailArtifactJar(
			project,
			outputJarTargetFile,
			artifactType,
			jvmComponent,
			implementationTitle,
			artifactDescription,
			roots,
			includedFiles,
			jars,
			zipFiles,
			directories,
			localConfig)
	}

	companion object
	{
		/**
		 * Create an [AvailArtifactJar].
		 *
		 * @param project
		 *   The active Gradle [Project].
		 * @param outputLocation
		 *   The Jar file location where the jar file will be written.
		 * @param artifactType
		 *   The [AvailArtifactType] of the [AvailArtifact] to create.
		 * @param jvmComponent
		 *   The [JvmComponent] if any to be used.
		 * @param implementationTitle
		 *   The title of the artifact being created that will be added to the
		 *   jar manifest ([Attributes.Name.IMPLEMENTATION_TITLE]).
		 * @param artifactDescription
		 *   The description of the [AvailArtifact] used in the
		 *   [AvailArtifactManifest].
		 * @param roots
		 *   The list of [AvailRootArtifactTarget]s to add to the artifact jar.
		 * @param includedFiles
		 *   The list of [File] - target directory inside artifact for it to be
		 *   placed [Pair]s.
		 * @param jars
		 *   The list of [JarFile]s to add to the artifact jar.
		 * @param zipFiles
		 *   The list of [ZipFile]s to add to the artifact jar.
		 * @param directories
		 *   The list of [File] directories whose contents should be added to
		 *   the artifact jar.
		 */
		fun createAvailArtifactJar (
			project: Project,
			outputLocation: String,
			artifactType: AvailArtifactType,
			jvmComponent: JvmComponent,
			implementationTitle: String,
			artifactDescription: String,
			roots: List<AvailRootArtifactTarget>,
			includedFiles: List<Pair<File, String>>,
			jars: List<JarFile>,
			zipFiles: List<ZipFile>,
			directories: List<File>,
			dependencyConfiguration: Configuration)
		{
			println("Creating $outputLocation…")
			File(outputLocation).apply {
				File(parent).mkdirs()
				delete()
			}
			val manifestMap = mutableMapOf<String, AvailManifestRoot>()
			roots.forEach { manifestMap[it.rootName] = it.availManifestRoot }

			val jarBuilder = AvailArtifactJarBuilder(
				outputLocation,
				project.version.toString(),
				implementationTitle,
				AvailArtifactManifest.manifestFile(
					artifactType,
					manifestMap,
					artifactDescription,
					jvmComponent))
			roots.forEach { jarBuilder.addRoot(it) }
			includedFiles.forEach { jarBuilder.addFile(it.first, it.second) }
			jars.forEach { jarBuilder.addJar(it) }
			zipFiles.forEach { jarBuilder.addZip(it) }
			directories.forEach { jarBuilder.addDir(it) }
			dependencyConfiguration.resolve().forEach {
				when
				{
					it.name.endsWith(".jar") ->
						jarBuilder.addJar(JarFile(it))
					it.name.endsWith(".zip") ->
						jarBuilder.addZip(ZipFile(it))
					it.isDirectory -> jarBuilder.addDir(it)
					else -> throw RuntimeException(
						"Failed to build $outputLocation: received dependency " +
							"${it.absolutePath} which did not resolved to a" +
							" jar, zip, or directory")
				}
			}
			jarBuilder.finish()
		}
	}
}
