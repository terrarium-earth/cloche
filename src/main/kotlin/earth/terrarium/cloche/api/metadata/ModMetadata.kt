package earth.terrarium.cloche.api.metadata

import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import javax.inject.Inject

@JvmDefaultWithoutCompatibility
interface ModMetadata {
    val modId: Property<String>
        @Input get

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

    val clientOnly: Property<Boolean>
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

    val objects: ObjectFactory
        @Inject get

    fun contributor(name: String) = contributor {
        it.name.set(name)
    }

    fun contributor(name: String, contact: String) = contributor {
        it.name.set(name)
        it.contact.set(contact)
    }

    fun contributor(action: Action<Person>) =
        contributors.add(objects.newInstance(Person::class.java).also(action::execute))

    fun author(name: String) = author {
        it.name.set(name)
    }

    fun author(name: String, contact: String) = author {
        it.name.set(name)
        it.contact.set(contact)
    }

    fun author(action: Action<Person>) =
        authors.add(objects.newInstance(Person::class.java).also(action::execute))

    fun dependency(action: Action<Dependency>) =
        dependencies.add(objects.newInstance(Dependency::class.java).also(action::execute))

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
            get

        val required: Property<Boolean>
            @Input
            @Optional
            get

        val objects: ObjectFactory
            @Inject get

        fun version(version: String) = version {
            it.start.set(version)
        }

        fun version(action: Action<VersionRange>) =
            version.set(objects.newInstance(VersionRange::class.java).also(action::execute))
    }
}
