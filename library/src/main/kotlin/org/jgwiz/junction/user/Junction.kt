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

/**
 * Describes what type of data this junction depends on.
 *
 * Some [Junction]s are designed to be reusable and should prefer
 * the [DependsOnKind.Context] option. This allows many different
 * graphs to leverage the same context fields regardless of how the context
 * was populated.
 *
 * Other [Junction]s are designed to be used specifically
 * with other [Junction]s to prevent collisions in behavior. These
 * are things like business logic pipelines which depend on a specific
 * set of user input. For this, prefer the [DependsOnKind.Junction].
 *
 * Finally, some types (like input Junctions) are forbidden from depending
 * on anything. In those instances, leverage [DependsOnKind.Nothing]
 *
 * Any graph can use any combination of these kinds, so long as all dependencies
 * are satisfied, with a few caveats:
 *
 * 1. Context kinds map to other Context kinds, and Junction kinds behave the same way.
 * 2. If multiple Context kinds are producing the same outputs, the engine will not know which one to pick.
 * 3. Prefer Junction kind unless you know that you have a generic, reusable use case.
 */
enum class DependsOnKind {
    Nothing,
    Context,
    Junction,
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

data object NoContext : RefinementContext

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class RefinementContextSpec(val id: String)

@Target(AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class JunctionMarker(val junctionKind: JunctionKind)

@Target(AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class JunctionDataMarker(val junctionKind: JunctionKind)

sealed interface JunctionResult {

    data class Success(
        val message: String? = null
    ): JunctionDataResult

    data class Failure(
        val error: Throwable,
        val message: String? = null,
    ) : JunctionDataResult

    data class Fanout<T : RefinementContext>(
        val refinementContextList: List<T>,
        val fanoutMessage: String? = null,
    ) : JunctionControlFlowResult
}

/**
 * This junction adjusted data which will affect
 * subsequent junction operation
 */
sealed interface JunctionDataResult : JunctionResult

/**
 * This junction adjusted the control flow which
 * will affect subsequent junction operation.
 *
 * Control flow operations should not fail and therefore
 * do not return success/fail results.
 */
sealed interface JunctionControlFlowResult : JunctionResult

enum class JunctionLogStatus {
    Trace,
    Debug,
    Info,
    Warn,
    Error,
    Fatal,
}

@Serializable
data class JunctionLog<T: RefinementContext>(
    val status: JunctionLogStatus,
    val startTime: Long,
    val endTime: Long,
    val message: String? = null,
    val stackTrace: String? = null,
    val childLogs: List<JunctionLog<T>>? = null,
    val splitContextList: List<T>? = null,
)

interface ExternalPortal


// TODO: This is a work in progress
//interface ConditionalJunction<T: RefinementContext>: Junction {
//    override val junctionType: JunctionType
//        get() = JunctionType.Internal
//
//    fun refine(context: T, whenTrue: ())
//}

interface JunctionInvoker {
    fun invoke(
        context: RefinementContext,
        inputData: Any?,
        portal: ExternalPortal?,
    ): JunctionResult
}

data class JunctionDefinition(
    val id: String,
    val kind: JunctionKind,
    val dependsOnKind: DependsOnKind = DependsOnKind.Junction,
    val dependsOn: List<String> = emptyList(),
    val contextAdded: List<String> = emptyList(),
    val junction: Junction,
    val portalId: String? = null,
    val inputDataClass: Class<*>? = null,
    val inputDataId: String? = null,
    val invoker: JunctionInvoker,
)

data class JunctionExecutionPlan(
    val junctionExecutionLayerList: List<Set<String>>
)

/**
 * A junction execution layer represents a
 * set of junction ids which can safely (and will) be run in parallel unless
 * described otherwise by the execution args of the runner.
 */


@Suppress("unused")
@JunctionMarker(JunctionKind.External)
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ExternalJunctionSpec(
    val id: String,
    val dependsOn: Array<String>,
    val dependsOnKind: DependsOnKind = DependsOnKind.Junction,
    val contextAdded: Array<String> = [],
    val requiredContextId: String,
    val externalPortalId: String,
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
    val frameworkId: String,
)

@Suppress("unused")
@JunctionMarker(JunctionKind.Initializing)
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class InputJunctionSpec(
    val id: String,
    val contextAdded: Array<String>,
    val requiredContextId: String,
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
    val dependsOnKind: DependsOnKind = DependsOnKind.Junction,
    val requiredContextId: String,
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

data class JunctionRunnerArgs<T: RefinementContext>(
    val targetJunctionId: String,
    val inputJunctionData: Map<String, Any> = emptyMap(),
    val context: T
)

interface JunctionGraph {
    val junctions: Map<String, JunctionDefinition>
    val portals: Map<String, ExternalPortal>
    val executionPlans: Map<String, JunctionExecutionPlan>
}

interface JunctionRunner {
    fun <T: RefinementContext> runJunction(config: JunctionRunnerArgs<T>, junctionGraph: JunctionGraph): JunctionLog<T>
}

data object EmptyExternalPortal : ExternalPortal

class InvalidJunctionException(message: String) : RuntimeException(message)


// TODO:
//  - Everywhere that we use checkNotNull is invalid because we need to return a JunctionLog
//    and we don't want to have any bleed-through of exception handling that isn't custom built
//    by the user themself.
//    DONE: We need to complete the internal mapping functions and rename them to something more appropriate
//    consistent and in line with the fact that they are mappers
//  - Right now we loop through children using a foreach and recursion, but we probably want to leverage
//    some flavor of parallelism by creating the DAG prior to the function call (like during processing)
//  - Introduce a DAG concept into the data model and correlate it to a Junction.
//  - Error is not an acceptable failure state, we need to handle that much more cleanly to handle FATAL, WARN, etc.
//  - Our junction result log stuff can't just return a junction log, because it may need to return a fanout
//    where we spin up multiple copied contexts
object DefaultJunctionRunner : JunctionRunner {
    override fun <T: RefinementContext> runJunction(
        config: JunctionRunnerArgs<T>,
        junctionGraph: JunctionGraph
    ): JunctionLog<T> {
        return processJunctionGraph(config.targetJunctionId, junctionGraph, config.inputJunctionData, config.context)
    }

    private fun <T : RefinementContext> processJunctionGraph(
        junctionId: String,
        junctionGraph: JunctionGraph,
        inputJunctionData: Map<String, Any>,
        context: T
    ): JunctionLog<T> {
        val startTime = System.currentTimeMillis()

        val executionPlan = junctionGraph.executionPlans[junctionId]
            ?: throw InvalidJunctionException("Execution plan for junction '$junctionId' not found")

        val allLayerLogs = mutableListOf<JunctionLog<T>>()

        executionPlan.junctionExecutionLayerList.forEach { executionLayer ->
            val layerLogs = executionLayer
                .toList()
                .parallelStream()
                .map { currentJunctionId ->
                    executeJunction(
                        currentJunctionId = currentJunctionId,
                        junctionGraph = junctionGraph,
                        inputJunctionData = inputJunctionData,
                        context = context,
                        startTime = startTime,
                    )
                }
                .toList()

            allLayerLogs += layerLogs
        }

        return allLayerLogs.lastOrNull()
            ?: JunctionLog(
                status = JunctionLogStatus.Info,
                startTime = startTime,
                endTime = System.currentTimeMillis(),
                message = "No junctions executed for target '$junctionId'",
            )
    }

    private fun <T : RefinementContext> executeJunction(
        currentJunctionId: String,
        junctionGraph: JunctionGraph,
        inputJunctionData: Map<String, Any>,
        context: T,
        startTime: Long
    ): JunctionLog<T> {
        val junctionDef = junctionGraph.junctions[currentJunctionId]
            ?: throw InvalidJunctionException("JunctionDefinition '$currentJunctionId' not found")

        var inputData: Any? = null
        var externalPortal: ExternalPortal? = null

        when (junctionDef.kind) {
            JunctionKind.External -> {
                val portalId = junctionDef.portalId
                    ?: throw InvalidJunctionException(
                        "External Portal is required for external JunctionKind types: ${junctionDef.id}"
                    )

                externalPortal = junctionGraph.portals[portalId]
                    ?: throw InvalidJunctionException(
                        "Portal '$portalId' not found for external junction '${junctionDef.id}'"
                    )
            }

            JunctionKind.Initializing -> {
                val inputDataId = junctionDef.inputDataId
                    ?: throw InvalidJunctionException(
                        "Input data is required for initializing JunctionKind types: ${junctionDef.id}"
                    )

                inputData = inputJunctionData[inputDataId]
                    ?: throw InvalidJunctionException(
                        "Input data '$inputDataId' not found in input data graph for junction '${junctionDef.id}'"
                    )
            }

            JunctionKind.Internal -> Unit
        }

        return when (val result = junctionDef.invoker.invoke(context, inputData, externalPortal)) {
            is JunctionDataResult -> junctionLogFromJunctionDataResult(result, emptyList(), startTime)
            is JunctionControlFlowResult -> junctionLogFromJunctionControlFlowResult(
                result,
                emptyList(),
                startTime
            )
        }
    }

    private fun <T: RefinementContext> junctionLogFromJunctionControlFlowResult(result: JunctionControlFlowResult, childLogs: List<JunctionLog<T>>, startTime: Long): JunctionLog<T> {
        return when (result) {
            is JunctionResult.Fanout<*> -> JunctionLog(
                message = result.fanoutMessage,
                splitContextList = result.refinementContextList as List<T>,
                startTime = startTime,
                endTime = System.currentTimeMillis(),
                childLogs = childLogs,
                status = JunctionLogStatus.Info,
            )
        }
    }

    private fun <T: RefinementContext> junctionLogFromJunctionDataResult(result: JunctionDataResult, childLogs: List<JunctionLog<T>>, startTime: Long) =
        when (result) {
            is JunctionResult.Failure -> {
                JunctionLog(
                    status = JunctionLogStatus.Error,
                    startTime = startTime,
                    endTime = System.currentTimeMillis(),
                    message = result.message,
                    childLogs = childLogs,
                    stackTrace = result.error.stackTrace.toString(),
                )
            }
            is JunctionResult.Success -> {
                JunctionLog(
                    status = JunctionLogStatus.Info,
                    startTime = startTime,
                    endTime = System.currentTimeMillis(),
                    message = result.message,
                    childLogs = childLogs,
                )
            }
        }



    private fun <T: RefinementContext> junctionLogFromFailedChildJunctionResult(childJunctionLogs: List<JunctionLog<T>>, startTime: Long, message: String) =
        JunctionLog(
            status = JunctionLogStatus.Error,
            startTime = startTime,
            childLogs = childJunctionLogs,
            endTime = System.currentTimeMillis(),
            message = message,
        )

}



