/*
 * AvailWorkbenchTask.kt
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

import org.availlang.artifact.AvailArtifact
import org.availlang.artifact.AvailArtifactType
import org.availlang.artifact.PackageType
import org.availlang.artifact.jar.JvmComponent
import org.availlang.artifact.roots.AvailRoot
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import java.io.File
import java.security.MessageDigest
import java.util.jar.Attributes
import java.util.jar.JarFile
import java.util.zip.ZipFile

/**
 * An object that contains the configuration state for building an Avail
 * artifact.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
@Suppress("unused")
class PackageAvailArtifact internal constructor(
	private val project: Project,
	private val availExtension: AvailExtension)
{
	/**
	 * The name of the [Configuration] that the dependencies are added to.
	 */
	private val configName = "_packageAvailArtifact"

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
	var artifactType: AvailArtifactType = AvailArtifactType.APPLICATION

	/**
	 * The [PackageType] of the target artifact build.
	 */
	var packageType: PackageType = PackageType.JAR

	/**
	 * The [MessageDigest] algorithm to use to create the digests for all the
	 * Avail roots' contents. This must be a valid algorithm accessible from
	 * [java.security.MessageDigest.getInstance].
	 */
	var artifactDigestAlgorithm: String = "SHA-256"

	/**
	 * The base name to give to the created artifact.
	 */
	var artifactName: String = project.name

	/**
	 * The version to give to the created artifact
	 * ([Attributes.Name.IMPLEMENTATION_VERSION]).
	 */
	var version: String = project.version.toString()

	/**
	 * The description of the artifact being build.
	 */
	private val artifactDescription: String get() =
		availExtension.projectDescription

	/**
	 * The [Attributes.Name.MAIN_CLASS] for the manifest or an empty string if
	 * no main class set. This should be the primary main class for starting
	 * the application.
	 */
	var jarManifestMainClass: String = ""

	/**
	 * The title of the artifact being created that will be added to the jar
	 * manifest ([Attributes.Name.IMPLEMENTATION_TITLE]).
	 */
	var implementationTitle: String = ""

	/**
	 * The [JvmComponent] that describes the JVM contents of the artifact of
	 * [JvmComponent.NONE] if no JVM components.
	 */
	var jvmComponent: JvmComponent = JvmComponent.NONE

	/**
	 * The absolute path to the directory location where the jar file is to be
	 * written.
	 *
	 * It is set to the following by default:
	 * ```
	 * "${project.buildDir}/libs/"
	 * ```
	 */
	var outputDirectory = "${project.buildDir}/libs/"

	/**
	 * The absolute path to the jar file that will be created.
	 *
	 * It is set to the following by default:
	 * ```
	 * "$outputDirectory$artifactName-$version.jar"
	 * ```
	 */
	@Suppress("MemberVisibilityCanBePrivate")
	val targetOutputJar: String get()
	{
		val suffix =
			if(version.isNotBlank()) { "-$version.jar" } else { ".jar" }
		return "$outputDirectory$artifactName$suffix"
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
	 *   the artifact jar.
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

	/**
	 * The map of [AvailRoot.name] to [AvailRoot] that will be included in this
	 * VM option,
	 */
	private val roots: List<AvailRoot> get() =
		availExtension.roots.values.toList()

	/**
	 * This is the core action that is performed.
	 */
	fun create ()
	{
		CreateAvailArtifactJar.createAvailArtifactJar(
			version,
			targetOutputJar,
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
}
