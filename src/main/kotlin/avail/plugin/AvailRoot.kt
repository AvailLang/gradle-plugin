/*
 * AvailRoot.kt
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

import org.availlang.artifact.AvailRootArtifactTarget
import org.availlang.artifact.manifest.AvailManifestRoot
import java.net.URI
import java.security.MessageDigest

/**
 * `AvailRoot` represents an Avail source root.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @property name
 *   The name of the root.
 * @property uri
 *   The String [URI] location of the root.
 * @property availModuleExtensions
 *   The file extensions that signify files that should be treated as Avail
 *   modules.
 * @property entryPoints
 *   The Avail entry points exposed by this root.
 * @property description
 *   An optional description of the root.
 * @property action
 *   A lambda that accepts this [AvailRoot] and is executed after all roots have
 *   been added.
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
open class AvailRoot constructor(
	val name: String,
	val uri: String,
	val availModuleExtensions: List<String> = listOf("avail"),
	val entryPoints: List<String> = listOf(),
	val description: String = "",
	var action: (AvailRoot) -> Unit = {}
) : Comparable<AvailRoot>
{
	/**
	 * Construct an [AvailRoot].
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


	/** The VM Options, `-DavailRoot`, root string. */
	val rootString: String by lazy { "$name=$uri" }

	/**
	 * The printable configuration for this root.
	 */
	internal open val configString: String get() = "\n\t$name ($uri)"

	/**
	 * Create an [AvailManifestRoot] from this [AvailRoot].
	 *
	 * @param digestAlgorithm
	 *   The [MessageDigest] algorithm to use to create the digests for all the
	 *   root's contents. This must be a valid algorithm accessible from
	 *   [java.security.MessageDigest.getInstance].
	 * @return
	 *   An [AvailManifestRoot].
	 */
	@Suppress("MemberVisibilityCanBePrivate")
	fun manifestRoot (digestAlgorithm: String): AvailManifestRoot =
		AvailManifestRoot(
			name,
			availModuleExtensions,
			entryPoints,
			description,
			digestAlgorithm)

	/**
	 * Create an [AvailRootArtifactTarget] from this [AvailRoot].
	 *
	 * @param digestAlgorithm
	 *   The [MessageDigest] algorithm to use to create the digests for all the
	 *   root's contents. This must be a valid algorithm accessible from
	 *   [java.security.MessageDigest.getInstance].
	 * @return
	 *   An [AvailRootArtifactTarget].
	 */
	fun availRootArtifactTarget (
		digestAlgorithm: String
	): AvailRootArtifactTarget =
		AvailRootArtifactTarget(uri, manifestRoot(digestAlgorithm))

	// Module packages always come before modules.
	override fun compareTo(other: AvailRoot): Int =
		when
		{
			this is CreateAvailRoot && other is CreateAvailRoot ||
			this !is CreateAvailRoot && other !is CreateAvailRoot ->
				name.compareTo(other.name)
			this is CreateAvailRoot && other !is CreateAvailRoot -> 1
			else ->
				// Therefore: this !is CreateAvailRoot && other is CreateAvailRoot
				-1
		}

	override fun toString(): String = rootString

	override fun equals(other: Any?): Boolean =
		when
		{
			this === other -> true
			other !is AvailRoot -> false
			name != other.name -> false
			uri != other.uri -> false
			else -> true
		}

	override fun hashCode(): Int
	{
		var result = name.hashCode()
		result = 31 * result + uri.hashCode()
		return result
	}
}
