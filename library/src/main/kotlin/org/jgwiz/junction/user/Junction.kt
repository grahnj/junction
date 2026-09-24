package org.jgwiz.junction.user

import kotlinx.serialization.Serializable

/**
 * ---------------------------------------------------------------------
 * ---------------------------------------------------------------------
 * Junction (noun)
 *
 * 1
 * : an act of joining : the state of being joined
 * 2
 * a
 * : a place or point of meeting
 * b
 * : an intersection of roads especially where one terminates
 * ---------------------------------------------------------------------
 */

/**
 * A junction is a formalized function
 * within a pipeline which follows a specific set of
 * standardizing rules as junctions of the same type
 */
sealed interface Junction

/**
 * A [JunctionKind] is a stage in the execution of domain
 * specific functionality where control is passed to an
 * external context in order to enrich the domain context
 * with the information necessary to complete the domain task.
 */
enum class JunctionKind {
    /**
     * Seeds the pipeline prior to execution with data known
     * before the execution, e.g. user input
     */
    Initializing,

    /**
     * Escapes the execution flow to access data that is needed
     * to enrich the context.
     */
    External,

    /**
     * An isolated piece of work which requires only the current context, typically
     * used for transforms, fan outs, and conditionals.
     */
    Internal,
}

interface InitializingJunction<T: RefinementContext, K> : Junction {
    fun refine(context: T, initializingData: K): JunctionResult
}

interface ExternalJunction<T: RefinementContext, K: ExternalPortal> : Junction {
    fun refine(context: T, externalAccess: K): JunctionResult
}

interface InternalJunction<T: RefinementContext> : Junction {
    fun refine(context: T): JunctionResult
}

interface FanoutJunction<T: RefinementContext> : Junction {
    fun refine(context: T): JunctionResult.Fanout<T>
}

/**
 * Describes the data which moves between [Junction]
 * boundaries as it is refined into its final state
 */
interface RefinementContext

@Target(AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class JunctionMarker(val junctionKind: JunctionKind)

@Target(AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class JunctionDataMarker(val junctionKind: JunctionKind)

sealed interface JunctionResult {

    data class Success(
        val message: String? = null
    ): JunctionResult

    data class Failure(
        val error: Throwable,
        val message: String? = null,
    ) : JunctionResult

    data class Fanout<T : RefinementContext>(
        val refinementContextList: List<T>,
        val fanoutMessage: String? = null,
    ) : JunctionResult
}

enum class JunctionLogStatus {
    Trace,
    Debug,
    Info,
    Warn,
    Error,
    Fatal,
}

@Serializable
data class JunctionLog(
    val status: JunctionLogStatus,
    val startTime: Long,
    val endTime: Long,
    val message: String? = null,
    val stackTrace: String? = null,
    val childLogs: List<JunctionLog>? = null,
)


interface ExternalPortal


// TODO: This is a work in progress
//interface ConditionalJunction<T: RefinementContext>: Junction {
//    override val junctionType: JunctionType
//        get() = JunctionType.Internal
//
//    fun refine(context: T, whenTrue: ())
//}

data class JunctionDefinition(
    val id: String,
    val kind: JunctionKind,
    val dependsOn: List<String> = emptyList(),
    val junction: Junction,
    val portalId: String? = null,
    val inputDataClass: Class<*>? = null
)

@Suppress("unused")
@JunctionMarker(JunctionKind.External)
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ExternalJunctionSpec(
    val id: String,
    val dependsOn: Array<String>,
)

/**
 * Annotates the portal class used to access external data
 * for a specific context. Currently only one portal will
 * be supported but it should be able to support multiple in the
 * future.
 *
 * [id]: The string identifier of the DI entity that needs to be retrieved
 * at runtime by this library so that it can be passed into
 * the external junctions
 */
@JunctionMarker(JunctionKind.External)
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ExternalJunctionPortalSpec(
    val id: String,
)

@Suppress("unused")
@JunctionMarker(JunctionKind.Initializing)
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class InputJunctionSpec(
    val id: String,
)

@Suppress("unused")
@JunctionDataMarker(JunctionKind.Initializing)
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class InputJunctionDataSpec(
    val id: String
)

@Suppress("unused")
@JunctionMarker(JunctionKind.Internal)
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class FanoutJunctionSpec(
    val id: String,
    val dependsOn: Array<String>,
)

enum class DependencyInjectionLibrary {
    Spring4,
}

@Suppress("unused")
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class JunctionGraphConfig(
    val packageName: String,
    val dependencyInjectionLibrary: DependencyInjectionLibrary
)

interface JunctionRunnerArgs {
    fun withTargetJunctionId(targetJunctionId: String): JunctionRunnerArgs
    fun withInputJunctionData(junctionId: String, inputJunctionData: Any): JunctionRunnerArgs
    fun withExternalPortalId(externalPortalId: String): JunctionRunnerArgs
}

interface JunctionGraph {
    val junctions: Map<String, JunctionDefinition>
    val portals: Map<String, ExternalPortal>
}

interface JunctionRunner {
    fun runJunction(config: JunctionRunnerArgs, junctionGraph: JunctionGraph): JunctionLog
}

data object EmptyExternalPortal : ExternalPortal

class DefaultJunctionRunnerArgs: JunctionRunnerArgs {
    var targetJunctionId : String? = null
        private set
    val inputJunctionDataMap = mutableMapOf<String, Any>()
    var externalPortalId: String? = null
        private set

    override fun withTargetJunctionId(targetJunctionId: String): JunctionRunnerArgs {
        check(this.targetJunctionId == null) { "Target Junction already set" }

        this.targetJunctionId = targetJunctionId

        return this
    }

    override fun withInputJunctionData(junctionId: String, inputJunctionData: Any): JunctionRunnerArgs {
        check(!this.inputJunctionDataMap.containsKey(junctionId)) { "Junction Input Data already defined for $junctionId" }

        this.inputJunctionDataMap[junctionId] = inputJunctionData

        return this
    }

    override fun withExternalPortalId(externalPortalId: String): JunctionRunnerArgs {
        check(this.externalPortalId == null) { "External Portal already defined, `add` has been called twice." }

        this.externalPortalId = externalPortalId

        return this
    }
}