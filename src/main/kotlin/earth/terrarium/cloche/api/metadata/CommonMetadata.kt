package earth.terrarium.cloche.api.metadata

import earth.terrarium.cloche.api.metadata.custom.JsonSerializable
import earth.terrarium.cloche.api.metadata.custom.convertToSerializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.kotlin.dsl.newInstance
import javax.inject.Inject

@JvmDefaultWithoutCompatibility
interface CommonMetadata {
    val name: Property<String>
        @Optional
        @Input
        get

    val description: Property<String>
        @Optional
        @Input
        get

    val license: Property<String>
        @Optional
        @Input
        get

    val icon: Property<String>
        @Optional
        @Input
        get

    val url: Property<String>
        @Optional
        @Input
        get

    val issues: Property<String>
        @Optional
        @Input
        get

    val sources: Property<String>
        @Optional
        @Input
        get

    val authors: ListProperty<Person>
        @Nested
        get

    val contributors: ListProperty<Person>
        @Nested
        get

    val dependencies: ListProperty<Dependency>
        @Nested
        get

    val custom: MapProperty<String, JsonSerializable>
        @Nested
        get

    val objects: ObjectFactory
        @Inject get

    fun useAsConventionFor(metadata: CommonMetadata) {
        metadata.name.convention(name)
        metadata.description.convention(description)
        metadata.license.convention(license)
        metadata.icon.convention(icon)
        metadata.url.convention(url)
        metadata.issues.convention(issues)
        metadata.sources.convention(sources)

        metadata.authors.addAll(authors)
        metadata.contributors.addAll(contributors)
        metadata.dependencies.addAll(dependencies)

        metadata.custom.putAll(custom)
    }

    fun set(other: CommonMetadata) {
        name.set(other.name)
        description.set(other.description)
        license.set(other.license)
        icon.set(other.icon)
        url.set(other.url)
        issues.set(other.issues)
        sources.set(other.sources)
        authors.set(other.authors)
        contributors.set(other.contributors)
        dependencies.set(other.dependencies)
        custom.set(other.custom)
    }

    fun contributor(name: String) = contributor {
        this.name.set(name)
    }

    fun contributor(name: String, contact: String) = contributor {
        this.name.set(name)
        this.contact.set(contact)
    }

    fun contributor(action: Action<Person>) =
        contributors.add(objects.newInstance<Person>().also(action::execute))

    fun author(name: String) = author {
        this.name.set(name)
    }

    fun author(name: String, contact: String) = author {
        this.name.set(name)
        this.contact.set(contact)
    }

    fun author(action: Action<Person>) =
        authors.add(objects.newInstance<Person>().also(action::execute))

    fun require(
        modId: String,
        version: String,
        reason: String? = null,
        ordering: Dependency.Ordering = Dependency.Ordering.None,
        environment: Environment = Environment.Both
    ) {
        dependency(modId, version, Dependency.Type.Required, reason, ordering, environment)
    }

    fun recommend(
        modId: String,
        version: String,
        reason: String? = null,
        ordering: Dependency.Ordering = Dependency.Ordering.None,
        environment: Environment = Environment.Both
    ) {
        dependency(modId, version, Dependency.Type.Recommended, reason, ordering, environment)
    }

    fun suggest(
        modId: String,
        version: String,
        reason: String? = null,
        ordering: Dependency.Ordering = Dependency.Ordering.None,
        environment: Environment = Environment.Both
    ) {
        dependency(modId, version, Dependency.Type.Suggested, reason, ordering, environment)
    }

    fun markConflict(
        modId: String,
        version: String,
        reason: String? = null,
        ordering: Dependency.Ordering = Dependency.Ordering.None,
        environment: Environment = Environment.Both
    ) {
        dependency(modId, version, Dependency.Type.Conflicts, reason, ordering, environment)
    }

    fun markIncompatible(
        modId: String,
        version: String,
        reason: String? = null,
        ordering: Dependency.Ordering = Dependency.Ordering.None,
        environment: Environment = Environment.Both
    ) {
        dependency(modId, version, Dependency.Type.Breaks, reason, ordering, environment)
    }

    fun dependency(
        modId: String,
        version: String,
        type: Dependency.Type = Dependency.Type.Required,
        reason: String? = null,
        ordering: Dependency.Ordering = Dependency.Ordering.None,
        environment: Environment = Environment.Both
    ) {
        dependency {
            this.modId.set(modId)
            this.version(version)
            this.type.set(type)

            if (reason != null) {
                this.reason.set(reason)
            }

            this.ordering.set(ordering)
            this.environment.set(environment)
        }
    }

    fun dependency(action: Action<Dependency>) =
        dependencies.add(objects.newInstance<Dependency>().also(action::execute))

    fun custom(vararg data: Pair<String, Any?>) = custom(mapOf(*data))

    fun custom(data: Map<String, Any?>) =
        custom.putAll(data.mapValues { (_, value) -> convertToSerializable(objects, value) })

    fun custom(name: String, value: Any?) =
        custom.put(name, convertToSerializable(objects, value))

    interface Person {
        val name: Property<String>
            @Input
            get

        val contact: Property<String>
            @Input
            @Optional
            get
    }

    interface VersionRange {
        val start: Property<String>
            @Optional
            @Input
            get

        val end: Property<String>
            @Optional
            @Input
            get

        val startInclusive: Property<Boolean>
            @Optional
            @Input
            get

        val endExclusive: Property<Boolean>
            @Optional
            @Input
            get
    }

    @JvmDefaultWithoutCompatibility
    interface Dependency {
        val modId: Property<String>
            @Input
            get

        val version: Property<VersionRange>
            @Nested
            @Optional
            get

        val type: Property<Type>
            @Input
            @Optional
            get

        val reason: Property<String>
            @Input
            @Optional
            get

        val ordering: Property<Ordering>
            @Input
            @Optional
            get

        val environment: Property<Environment>
            @Input
            @Optional
            get

        val objects: ObjectFactory
            @Inject get

        fun version(version: String) = version {
            this.start.set(version)
        }

        fun version(action: Action<VersionRange>) =
            version.set(objects.newInstance<VersionRange>().also(action::execute))

        enum class Type {
            Required,
            Recommended,
            Suggested,
            Conflicts,
            Breaks,
        }

        enum class Ordering {
            @SerialName("NONE") None,
            @SerialName("BEFORE") Before,
            @SerialName("AFTER") After,
        }
    }

    @Serializable
    enum class Environment {
        @SerialName("client") Client,
        @SerialName("server") Server,
        @SerialName("*") Both,
    }
}
