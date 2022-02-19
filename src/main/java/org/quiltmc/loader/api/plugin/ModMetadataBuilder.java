package org.quiltmc.loader.api.plugin;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.api.ModContributor;
import org.quiltmc.loader.api.ModDependency;
import org.quiltmc.loader.api.ModLicense;
import org.quiltmc.loader.api.Version;
import org.quiltmc.loader.impl.metadata.qmj.V1ModMetadataBuilder;

public interface ModMetadataBuilder {

	public static ModMetadataBuilder of(LoaderValue.LObject root, String id, String group, Version version) {
		return V1ModMetadataBuilder.of(root, id, group, version);
	}

	public static ModMetadataBuilder ofTentative(LoaderValue.LObject root, String id, String group, Version version) {
		return V1ModMetadataBuilder.ofTentative(root, id, group, version);
	}

	FullModMetadata build();

	// Required Fields

	String id();

	String group();

	Version version();

	// Optional Fields

	String name();

	ModMetadataBuilder name(String name);

	String description();

	ModMetadataBuilder description(String description);

	/** @return The (modifiable) list of licenses. */
	List<ModLicense> licenses();

	ModMetadataBuilder addLicense(ModLicense license);

	ModMetadataBuilder addLicenses(Collection<ModLicense> licenses);

	/** Indicates that the license field is complete, but empty.
	 * 
	 * @throws IllegalStateException if this was not constructed via
	 *             {@link #ofTentative(org.quiltmc.loader.api.LoaderValue.LObject, String, String, Version)}. */
	ModMetadataBuilder noLicenses();

	/** @return The (modifiable) list of contributors. */
	List<ModContributor> contributors();

	ModMetadataBuilder addContributor(ModContributor license);

	ModMetadataBuilder addContributors(Collection<ModContributor> licenses);

	/** Indicates that the contributors field is complete, but empty.
	 * 
	 * @throws IllegalStateException if this was not constructed via
	 *             {@link #ofTentative(org.quiltmc.loader.api.LoaderValue.LObject, String, String, Version)}. */
	ModMetadataBuilder noContributors();

	Map<String, String> contactInfo();

	ModMetadataBuilder addContactInfo(String key, String value);

	ModMetadataBuilder putContactInfo(Map<String, String> map);

	// Dependency Information

	ModMetadataBuilder addDepends(ModDependency dep);

	ModDependencyBuilder startDepends();

	// Usage

	/** @see FullModMetadata#isQuiltDeps() */
	void setIsQuiltDeps(boolean quiltShouldGen);

	public interface ModDependencyBuilder {

		ModDependency toDependency();

		/** Passes {@link #toDependency()} into {@link ModMetadataBuilder#addDepends(ModDependency)} and returns the
		 * original {@link ModMetadataBuilder} */
		ModMetadataBuilder build();
	}
}