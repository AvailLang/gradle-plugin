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

import avail.plugin.AvailPlugin.Companion.AVAIL
import org.availlang.artifact.AvailArtifactType
import org.availlang.artifact.jar.AvailArtifactJarBuilder
import org.availlang.artifact.jar.JvmComponent
import org.availlang.artifact.manifest.AvailArtifactManifest
import org.availlang.artifact.manifest.AvailManifestRoot
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.net.URI
import java.util.jar.Attributes
import java.util.jar.JarFile
import java.util.zip.ZipFile

/**
 * A task that assembles the entire Avail project into a fat jar.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
@Suppress("unused")
open class PackageAvailArtifactTask: DefaultTask()
{
	/**
	 * The digest algorithm to use to create the digests for root's contents.
	 */
	@Input
	var digestAlgorithm: String = "SHA-256"

	/**
	 * The base name to give to the created jar.
	 */
	@Input
	var artifactName: String = name

	/**
	 * The base name to give to the created jar
	 * ([Attributes.Name.IMPLEMENTATION_VERSION]).
	 */
	@Input
	var version: String = ""

	/**
	 * The [Attributes.Name.MAIN_CLASS] for the manifest or an empty string if
	 * no main class set.
	 */
	@Input
	var jarManifestMainClass: String = ""

	/**
	 * The title of the artifact being created that will be added to the jar
	 * manifest ([Attributes.Name.IMPLEMENTATION_TITLE]).
	 */
	@Input
	var implementationTitle: String = ""

	/**
	 * The description of the artifact being build.
	 */
	@Input
	var artifactDescription: String = ""

	/**
	 * The [AvailArtifactType] of the artifact be built.
	 */
	@Input
	var artifactType: AvailArtifactType = AvailArtifactType.LIBRARY

	/**
	 * The [JvmComponent] that describes the JVM contents of the artifact of
	 * [JvmComponent.NONE] if no JVM components.
	 */
	@Input
	var jvmComponent: JvmComponent = JvmComponent.NONE

	/**
	 * The name of the [Configuration] that the dependencies are added to.
	 */
	private val configName = "_configAvailArtifact$name"

	/**
	 * The [Configuration] for adding [dependencies][Dependency] to be included
	 * in the workbench jar.
	 */
	private val localConfig: Configuration by lazy {
		project.configurations.create(configName)
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
	 * Get the active [AvailExtension] from the host [Project].
	 */
	private val availExtension: AvailExtension get() =
		(project.extensions.getByName(AVAIL) as AvailExtension)

	/**
	 * The map of [AvailRoot.name] to [AvailRoot] that will be included in this
	 * VM option,
	 */
	private val roots = mutableMapOf<String, AvailRoot>()

	/**
	 * Add an Avail root with the provided name and [URI] to be added to the
	 * workbench when it is launched.
	 *
	 * There is no need to prefix the file scheme, `file://`, if it exists on
	 * the local file system; otherwise the scheme should be prefixed.
	 *
	 * @param name
	 *   The name of the root to add.
	 * @param uri
	 *   The uri path to the root.
	 * @param description
	 *   An optional description of the root.
	 */
	@Suppress("Unused")
	fun root(name: String, uri: String, description: String = "")
	{
		roots[name] = AvailRoot(name, uri, description) { }
	}

	/**
	 * This is the core action that is performed.
	 */
	@TaskAction
	protected fun run ()
	{
		val v = if (version.isNotEmpty()) "-$version" else ""
		val fullPathToFile =
			"${project.buildDir}/$AVAIL/$artifactName$v.jar"
		File("${project.buildDir}/$AVAIL/").mkdirs()
		val rootMap = mutableMapOf<String, AvailManifestRoot>()
		roots.forEach { (t, u) ->
			rootMap[t] = AvailManifestRoot(
				t,
				listOf(".avail"),
				listOf("!_"),
				u.description)
		}


		val jarBuilder = AvailArtifactJarBuilder(
			fullPathToFile,
			project.version.toString(),
			artifactName,
			AvailArtifactManifest.manifestFile(
				artifactType,
				rootMap,
				artifactDescription,
				JvmComponent.NONE),
			jarManifestMainClass)

		localConfig.resolve().forEach {
			when
			{
				it.name.endsWith(".jar") -> jarBuilder.addJar(JarFile(it))
				it.name.endsWith(".zip") -> jarBuilder.addZip(ZipFile(it))
				it.isDirectory -> jarBuilder.addDir(it)
				else -> throw RuntimeException(
					"Failed to build $fullPathToFile: received " +
						"${it.absolutePath} which is not a jar, zip, or " +
						"directory")
			}
		}
		roots.forEach { (k,v) ->
			jarBuilder.addRoot(v.uri, v.name, digestAlgorithm)
		}
		jarBuilder.finish()

	}
}
