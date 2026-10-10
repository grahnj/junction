# Junction

Junction is an annotation library that works with your dependency injection framework (currently Spring) 
to generate data pipelines for you while allowing you to keep your code organized in a manner similar to your
dependency injection frameworks existing patterns.

## Data Pipelines
Automated data is hard. Upstream levers act as business decisions, and complex business data
and scenarios can easily derail the best laid plans. Junction data pipelines offer the following solutions
to these problems:
- Generated DAG execution plans
- Automatic logging and timing
- Replayability
- Simple, pattern agnostic annotations
- Automatic parallelization
- Junction reusability

## How it works

For a more comprehensive journey, feel free to download and run the spring-sample-app
which details all the necessary configurations to run a very basic Junction data pipeline.

```kotlin
// config/JunctionConfig.kt
@JunctionGraphConfig(
    packageName = "org.your.package.destination",
    dependencyInjectionLibrary = DependencyInjectionLibrary.Spring4,
)
object JunctionConfig
```

Define a simple configuration for you application

_*Note: Junction relies heavily on kotlin object types which do not necessitate instantiation.*_

```kotlin
// entity/Entity.kt
data class Student(val id: Int)

@RefinementContextSpec("refinement-context.university")
data class UniversityPipelineData(
    var studentList: List<Student>? = null,
    var classroomId: Int? = null,
    val studentId: Int? = null,
    var instructorId: Int? = null,

    ) : RefinementContext
```
The `RefinmentContext` uses a "fat struct" concept to describe a wide
scenario of data points across a single context with one data class. Most of these data
points will be left empty for the duration of a pipeline refinement.

It is safe to use multiple `RefinementContext` entities in a single project, but 
a junction (and its entire associated pipeline) must be contained to a single `RefinmentContext`.

```kotlin
// service/UniversityJunctionPortal.kt
@Service("universityJunctionExternalPortal")
@ExternalJunctionPortalSpec(
    id = "external-portal.university",
    frameworkId = "universityJunctionExternalPortal"

)
class UniversityJunctionExternalPortal : ExternalPortal {
    fun getStudentsByClassroomId(classroomId: Int): List<Student> =
        listOf(Student(1), Student(2), Student(3))
}
```

For this example, we're just hard-coding a return value, but there are a few
things to take note of here:

1. This is a class, not an object. The assumption is that you will have stateful dependencies and that an instance of this class will be pulled from your DI Framework
2. Related to the point above, you would typically have an `@Autowired` constructor here and have this thing marked
as `Component` or `Service` (as a common Spring example)
3. The portal is exactly what it sounds like -- junction is a stateless, purely functional
mechanism. The portal is your gateway to the outside world. Need to access a database? Wire it up through a portal.

```kotlin
// service/UniversityJunctionService.kt
@Service
class UniversityJunctionService @Autowired constructor(
    private val junctionGraph: JunctionGraph,
) {

    fun runJunction(junctionId: String): JunctionLog<UniversityPipelineData> =
        runJunction(JunctionRunnerArgs(
            targetJunctionId = junctionId,
            inputJunctionData = emptyMap(),
            context = UniversityPipelineData(),
        ))

    fun runJunction(junctionId: String, inputs: Map<String, Any>): JunctionLog<UniversityPipelineData> =
        runJunction(JunctionRunnerArgs(
            targetJunctionId = junctionId,
            inputJunctionData = inputs,
            context = UniversityPipelineData(),
        ))


    private fun runJunction(
        config: JunctionRunnerArgs<UniversityPipelineData>,
    ): JunctionLog<UniversityPipelineData> =
        DefaultJunctionRunner.runJunction(config, junctionGraph)
}
```
This is just a basic implementation of some runJunction examples, but this service
is what you will call to run ALL of your junctions. There's a few things worth calling
out in this file:

1. `DefaultJunctionRunner` is my implementation of the `JunnerRunner` interface, and can be used directly or re-implemented.
2. `JunctionGraph` is NEVER implemented. The included `ksp` library will automatically
generate the implementation of the JunctionGraph and have it ready for your DI Framework to scan.
3. `inputJunctionData` can be a bit confusing, but it is meant to enable multiple inputs at once
of different types. You'll see in the following files how to set it up so you never have to think about 
it unless you really want multiple inputs at once.

```kotlin
//junction/SampleJunctions.kt
@InputJunctionSpec(
    id = "input.classroom-id",
    contextAdded = "context.classroom-id",
    requiredContextId = "refinement-context.university",
)
data object InputJunctionSample: InitializingJunction<UniversityPipelineData, InputJunctionSample.UserInput> {

    @InputJunctionDataSpec("input.classroom-id")
    data class UserInput(
        val classroomId: Int,
    )

    override fun refine(context: UniversityPipelineData, initializingData: UserInput): JunctionResult {
        context.classroomId = initializingData.classroomId

        return JunctionResult.Success()
    }
}


@ExternalJunctionSpec(
    id = "junction.get-student-list-by-classroom-id",
    dependsOn = ["context.classroom-id"],
    dependsOnKind = DependsOnKind.Context,
    externalPortalId = "external-portal.university",
    requiredContextId = "refinement-context.university",
)
object GetStudentListByClassroomId : ExternalJunction<UniversityPipelineData, UniversityJunctionExternalPortal> {
    // here we are setting up a local context to harden exactly which fields
    // we should be using when we run refine
    data class LocalContext(
        private val fullContext: UniversityPipelineData
    ) {
        val classroomId: Int get() = fullContext.classroomId!!
    }

    override fun refine(
        context: UniversityPipelineData,
        externalAccess: UniversityJunctionExternalPortal,
    ) : JunctionResult {
        val localContext = LocalContext(context)

        try {
            val response = externalAccess.getStudentsByClassroomId(
                localContext.classroomId,
            )

            context.studentList = response

            return JunctionResult.Success(
                message = "${response.size} students returned for classroomId ${localContext.classroomId}",
            )
        } catch (e: Exception) {
            return JunctionResult.Failure(
                error = Error(e),
            )
        }
    }
}

@FanoutJunctionSpec(
    id = "junction.fanout.split-context-by-student",
    dependsOn = ["junction.get-student-list-by-classroom-id"],
    requiredContextId = "refinement-context.university",
)
object FanOutStudentListForStudentContext : FanoutJunction<UniversityPipelineData> {
    data class LocalContext(
        val fullContext: UniversityPipelineData,
    ) {
        val studentList: List<Student> = fullContext.studentList!!
    }

    override fun refine(context: UniversityPipelineData): JunctionResult.Fanout<UniversityPipelineData> {
        val localContext = LocalContext(context)

        return localContext
            .studentList
            .map {
                localContext.fullContext.copy(
                    studentList = null,
                    studentId = it.id
                )
            }
            .let {
                JunctionResult.Fanout(
                    it,
                )
            }
    }
}
```

Once the code is built, it will generate a graph automatically for you. If you're interested
in the content of this graph, download this repo and build the sample app yourself. The generated
files are located in `build/generated`.


This codebase and example documentation is a WIP. 


## Build Tooling

This project uses [Gradle](https://gradle.org/).
To build and run the application, use the *Gradle* tool window by clicking the Gradle icon in the right-hand toolbar,
or run it directly from the terminal:

* Run `./gradlew run` to build and run the application.
* Run `./gradlew build` to only build the application.
* Run `./gradlew check` to run all checks, including tests.
* Run `./gradlew clean` to clean all build outputs.

Note the usage of the Gradle Wrapper (`./gradlew`).
This is the suggested way to use Gradle in production projects.

[Learn more about the Gradle Wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html).

[Learn more about Gradle tasks](https://docs.gradle.org/current/userguide/command_line_interface.html#common_tasks).

This project follows the suggested multi-module setup and consists of the `app` and `utils` subprojects.
The shared build logic was extracted to a convention plugin located in `buildSrc`.

This project uses a version catalog (see `gradle/libs.versions.toml`) to declare and version dependencies
and both a build cache and a configuration cache (see `gradle.properties`).