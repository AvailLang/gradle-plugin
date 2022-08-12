package avail.plugin

import org.availlang.artifact.manifest.AvailManifestRoot
import org.gradle.api.Project
import java.net.URI

/**
 * `CreateAvailRoot` is an [AvailRoot] that is intended to be created.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @constructor
 * Construct an [AvailRoot].
 *
 * @param name
 *   The name of the root.
 * @param uri
 *   The String [URI] location of the root.
 * @param availModuleExtensions
 *   The file extensions that signify files that should be treated as Avail
 *   modules.
 * @param entryPoints
 *   The Avail entry points exposed by this root.
 * @param description
 *   An optional description of the root.
 * @param action
 *   A lambda that accepts this [AvailRoot] and is executed after all roots have
 *   been added.
 */
class CreateAvailRoot constructor(
	name: String,
	uri: String,
	availModuleExtensions: List<String> = listOf("avail"),
	entryPoints: List<String> = listOf(),
	description: String = "",
	action: (AvailRoot) -> Unit = {}
) : AvailRoot(name, uri, availModuleExtensions, entryPoints, description, action)
{
	/**
	 * Construct a [CreateAvailRoot].
	 *
	 * @param uri
	 *   The String [URI] location of the root.
	 * @param manifestRoot
	 *   The [AvailManifestRoot] that describes this [AvailRoot].
	 * @param action
	 *   A lambda that accepts this [AvailRoot] and is executed after all roots have
	 *   been added.
	 */
	constructor(
		uri: String,
		manifestRoot: AvailManifestRoot,
		action: (AvailRoot) -> Unit
	): this(
		manifestRoot.name,
		uri,
		manifestRoot.availModuleExtensions,
		manifestRoot.entryPoints,
		manifestRoot.description,
		action)

	override val configString: String get() = buildString {
		append("\n\t\t$name")
		append("\n\t\t\tRoot Contents:")
		this@CreateAvailRoot.appendRootHierarchy(this)
	}

	/**
	 * Add an [AvailModule] with the given name to the top level of this
	 * [CreateAvailRoot].
	 *
	 * @param name
	 *   The name of the [AvailModule] to create and add.
	 * @param extension
	 *   The Module's file extension. Defaults to `"avail"`.
	 *   Do not prefix with ".".
	 * @return
	 *   The created [AvailModule].
	 */
	@Suppress("unused")
	fun module (name: String, extension: String = "avail"): AvailModule =
		AvailModule(name, extension).apply {
			modules.add(this)
		}

	/**
	 * Add an [AvailModulePackage] with the given name to the top level of this
	 * [CreateAvailRoot].
	 *
	 * @param name
	 *   The name of the [AvailModulePackage] to create and add.
	 * @param extension
	 *   The Module's file extension. Defaults to `"avail"`.
	 *   Do not prefix with ".".
	 * @return
	 *   The created [AvailModulePackage].
	 */
	@Suppress("unused")
	fun modulePackage (
		name: String, extension: String = "avail"): AvailModulePackage =
			AvailModulePackage(name, extension).apply {
				modulePackages.add(this)
			}

	/**
	 * The set of [AvailModule]s to add to the top level of this [AvailRoot].
	 */
	private val modules =
		mutableSetOf<AvailModule>()

	/**
	 * The set of [AvailModulePackage]s to add to the top level of this
	 * [AvailRoot].
	 */
	private val modulePackages =
		mutableSetOf<AvailModulePackage>()

	/**
	 * Create the [modules] and [modulePackages] in [roots directory][uri].
	 *
	 * @param project
	 *   The host [Project] running the Avail Plugin.
	 */
	internal fun create (project: Project, extension: AvailExtension)
	{
		modulePackages.forEach {
			if (it.moduleHeaderCommentBody.isEmpty()
				&& extension.moduleHeaderCommentBody.isNotEmpty())
			{
				it.moduleHeaderCommentBody = extension.moduleHeaderCommentBody
			}
			it.create(project, uri)
		}
		modules.forEach {
			it.create(project, uri)
		}
	}

	/**
	 * Append a printable tree representation of this entire root.
	 *
	 * @param sb
	 *   The [StringBuilder] to add the hierarchy to.
	 */
	fun appendRootHierarchy (sb: StringBuilder)
	{
		modulePackages.forEach { it.hierarchyPrinter(1, sb) }
		modules.forEach { it.hierarchyPrinter(1, sb) }
	}
}
