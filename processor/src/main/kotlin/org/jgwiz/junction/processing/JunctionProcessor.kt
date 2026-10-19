package org.jgwiz.junction.processing

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import org.jgwiz.junction.user.DependencyInjectionLibrary
import org.jgwiz.junction.user.DependsOnKind
import org.jgwiz.junction.user.ExternalJunctionPortalSpec
import org.jgwiz.junction.user.ExternalJunctionSpec
import org.jgwiz.junction.user.InputJunctionDataSpec
import org.jgwiz.junction.user.InputJunctionSpec
import org.jgwiz.junction.user.JunctionExecutionPlan
import org.jgwiz.junction.user.JunctionGraphConfig
import org.jgwiz.junction.user.JunctionKind
import org.jgwiz.junction.user.RefinementContextSpec
import org.jgwiz.junction.user.TransformJunctionSpec

const val CONTEXT_ONLY_PASS_THRESHOLD = 1

class JunctionProcessor(
    private val env: SymbolProcessorEnvironment
) : SymbolProcessor {

    private val logger = env.logger
    private val codeGenerator = env.codeGenerator

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val graphConfig = resolver.getSymbolsWithAnnotation(
            JunctionGraphConfig::class.qualifiedName!!
        ).filterIsInstance<KSClassDeclaration>().firstOrNull()

        if (graphConfig == null) {
            logger.warn("No @JunctionGraphConfig found; skipping junction graph generation.")
            return emptyList()
        }

        val packageName = graphConfig.annotations
            .first { it.shortName.asString() == "JunctionGraphConfig" }
            .arguments
            .first { it.name?.asString() == "packageName" }
            .value as String

        val diEnumEntry = graphConfig.annotations
            .first { it.shortName.asString() == "JunctionGraphConfig" }
            .arguments
            .first { it.name?.asString() == "dependencyInjectionLibrary" }
            .value as? KSClassDeclaration
            ?: error("dependencyInjectionLibrary must be an enum")

        val diFramework = DependencyInjectionLibrary.valueOf(
            diEnumEntry.simpleName.asString()
        )

        val externalJunctions = resolver.getSymbolsWithAnnotation(
            ExternalJunctionSpec::class.qualifiedName!!
        ).filterIsInstance<KSClassDeclaration>()

        val inputJunctions = resolver.getSymbolsWithAnnotation(
            InputJunctionSpec::class.qualifiedName!!
        ).filterIsInstance<KSClassDeclaration>()

        val inputJunctionData = resolver.getSymbolsWithAnnotation(
            InputJunctionDataSpec::class.qualifiedName!!
        ).filterIsInstance<KSClassDeclaration>()

        val transformJunctions = resolver.getSymbolsWithAnnotation(
            TransformJunctionSpec::class.qualifiedName!!
        ).filterIsInstance<KSClassDeclaration>()

        val portals = resolver.getSymbolsWithAnnotation(
            ExternalJunctionPortalSpec::class.qualifiedName!!
        ).filterIsInstance<KSClassDeclaration>()

        val contexts = resolver.getSymbolsWithAnnotation(
            RefinementContextSpec::class.qualifiedName!!
        ).filterIsInstance<KSClassDeclaration>()

        val junctionMetas = mutableListOf<JunctionMeta>()
        val portalMetas = mutableListOf<PortalMeta>()
        val contextMetas = mutableListOf<ContextMeta>()

        externalJunctions.forEach { collectExternalJunction(it, junctionMetas) }
        inputJunctions.forEach { inputJunctionDecl ->
            val inputDataClassDecl = inputJunctionData.first { decl ->
                val dataDecl = decl.annotations.first {
                    it.shortName.asString() == "InputJunctionDataSpec"
                }

                val dataDeclJunctionId = dataDecl.arguments
                    .first { it.name?.asString() == "id" }
                    .value as String

                val inputDecl = inputJunctionDecl.annotations.first {
                    it.shortName.asString() == "InputJunctionSpec"
                }

                val inputDeclJunctionId = inputDecl.arguments
                    .first { it.name?.asString() == "id" }
                    .value as String

                dataDeclJunctionId == inputDeclJunctionId
            }

            collectInputJunction(inputJunctionDecl, inputDataClassDecl, junctionMetas)

        }
        transformJunctions.forEach { collectTransformJunction(it, junctionMetas)}

        portals.forEach { collectPortal(it, portalMetas) }
        contexts.forEach { collectContext(it, contextMetas) }

        generateGraph(packageName, diFramework, junctionMetas, portalMetas, contextMetas)

        return emptyList()
    }

    private fun collectExternalJunction(
        decl: KSClassDeclaration,
        out: MutableList<JunctionMeta>
    ) {
        val annotation = decl.annotations
            .first { it.shortName.asString() == "ExternalJunctionSpec" }

        val id = annotation.arguments
            .first { it.name?.asString() == "id" }
            .value as String

        val dependsOnArg = annotation.arguments
            .first { it.name?.asString() == "dependsOn" }
            .value as List<*>

        val dependsOnKindString = annotation.arguments
            .firstOrNull { it.name?.asString() == "dependsOnKind" }
            ?.value as? KSClassDeclaration
            ?: throw NoSuchElementException("DependOnKind is not found on the external junction")

        val contextAddedArg = annotation.arguments
            .first { it.name?.asString() == "contextAdded" }
            .value as List<*>

        val portalId = annotation.arguments
            .first { it.name?.asString() == "externalPortalId" }
            .value as String

        val refinementContextId = annotation.arguments
            .first { it.name?.asString() == "refinementContextId" }
            .value as String

        val dependsOnKind = DependsOnKind.valueOf(dependsOnKindString.simpleName.asString())

        val dependsOn = dependsOnArg.filterIsInstance<String>()
        val contextAdded = contextAddedArg.filterIsInstance<String>()

        out += JunctionMeta(
            id = id,
            kind = JunctionKind.External,
            dependsOn = dependsOn,
            dependsOnKind = dependsOnKind,
            contextAdded = contextAdded,
            qualifiedName = decl.qualifiedName!!.asString(),
            simpleName = decl.simpleName.asString(),
            portalId = portalId,
            inputDataClass = null,
            refinementContextId = refinementContextId,
        )
    }

    private fun collectInputJunction(
        decl: KSClassDeclaration,
        dataDecl: KSClassDeclaration,
        out: MutableList<JunctionMeta>
    ) {
        val annotation = decl.annotations
            .first { it.shortName.asString() == "InputJunctionSpec" }

        val id = annotation.arguments
            .first { it.name?.asString() == "id" }
            .value as String

        val dataId = dataDecl
            .annotations
            .first { it.shortName.asString() == "InputJunctionDataSpec" }
            .arguments
            .first { it.name?.asString() == "id" }
            .value as String

        val contextAddedArg = annotation.arguments
            .first { it.name?.asString() == "contextAdded" }
            .value as List<*>

        val refinementContextId = annotation.arguments
            .first { it.name?.asString() == "refinementContextId" }
            .value as String

        val contextAdded = contextAddedArg.filterIsInstance<String>()

        // Input data type is annotated separately with @InputJunctionDataSpec
        // We only store the junction here; processor can be extended later
        out += JunctionMeta(
            id = id,
            kind = JunctionKind.Initializing,
            dependsOn = emptyList(),
            dependsOnKind = DependsOnKind.Nothing,
            contextAdded = contextAdded,
            qualifiedName = decl.qualifiedName!!.asString(),
            simpleName = decl.simpleName.asString(),
            portalId = null,
            inputDataClass = dataDecl.qualifiedName!!.asString(),
            inputDataId = dataId,
            refinementContextId = refinementContextId,
        )
    }

    private fun collectTransformJunction(
        decl: KSClassDeclaration,
        out: MutableList<JunctionMeta>,
    ) {
        val annotation = decl.annotations
            .first { it.shortName.asString() == "TransformJunctionSpec" }

        val id = annotation.arguments
            .first { it.name?.asString() == "id" }
            .value as String

        val dependsOnArg = annotation.arguments
            .first { it.name?.asString() == "dependsOn" }
            .value as List<*>

        val dependsOnKindString = annotation.arguments
            .firstOrNull { it.name?.asString() == "dependsOnKind" }
            ?.value as? KSClassDeclaration
            ?: throw NoSuchElementException("DependOnKind is not found on the external junction")

        val refinementContextId = annotation.arguments
            .first { it.name?.asString() == "refinementContextId" }
            .value as String

        val contextAddedArg = annotation.arguments
            .first { it.name?.asString() == "contextAdded" }
            .value as List<*>

        val dependsOn = dependsOnArg.filterIsInstance<String>()
        val contextAdded = contextAddedArg.filterIsInstance<String>()

        val dependsOnKind = DependsOnKind.valueOf(dependsOnKindString.simpleName.asString())

        out += JunctionMeta(
            id = id,
            kind = JunctionKind.Internal,
            dependsOn = dependsOn,
            dependsOnKind = dependsOnKind,
            contextAdded = contextAdded,
            qualifiedName = decl.qualifiedName!!.asString(),
            simpleName = decl.simpleName.asString(),
            refinementContextId = refinementContextId,
            portalId = null,
            inputDataClass = null
        )
    }

    private fun collectFanoutJunction(
        decl: KSClassDeclaration,
        out: MutableList<JunctionMeta>,
    ) {
        val annotation = decl.annotations
            .first { it.shortName.asString() == "FanoutJunctionSpec" }

        val id = annotation.arguments
            .first { it.name?.asString() == "id" }
            .value as String

        val contextAddedArg = annotation.arguments
            .first { it.name?.asString() == "contextAdded" }
            .value as List<*>

        val dependsOnArg = annotation.arguments
            .first { it.name?.asString() == "dependsOn" }
            .value as List<*>

        val refinementContextId = annotation.arguments
            .first { it.name?.asString() == "refinementContextId" }
            .value as String

        val dependsOn = dependsOnArg.filterIsInstance<String>()
        val contextAdded = contextAddedArg.filterIsInstance<String>()

        out += JunctionMeta(
            id = id,
            kind = JunctionKind.Internal,
            dependsOn = dependsOn,
            dependsOnKind = DependsOnKind.Junction,
            contextAdded = contextAdded,
            qualifiedName = decl.qualifiedName!!.asString(),
            simpleName = decl.simpleName.asString(),
            refinementContextId = refinementContextId,
            portalId = null,
            inputDataClass = null
        )
    }

    private fun collectPortal(
        decl: KSClassDeclaration,
        out: MutableList<PortalMeta>
    ) {
        val annotation = decl.annotations
            .first { it.shortName.asString() == "ExternalJunctionPortalSpec" }

        val id = annotation.arguments
            .first { it.name?.asString() == "id" }
            .value as String

        val frameworkId = annotation.arguments
            .first { it.name?.asString() == "frameworkId" }
            .value as String

        out += PortalMeta(
            id = id,
            qualifiedName = decl.qualifiedName!!.asString(),
            frameworkId = frameworkId
        )
    }

    private fun collectContext(
        decl: KSClassDeclaration,
        out: MutableList<ContextMeta>
    ) {
        val annotation = decl.annotations
            .first { it.shortName.asString() == "RefinementContextSpec" }

        val id = annotation.arguments
            .first { it.name?.asString() == "id" }
            .value as String

        out += ContextMeta(
            id = id,
            qualifiedName = decl.qualifiedName!!.asString()
        )
    }

    data class ExecutionPlanOutput(
        val executionPlans: Map<String, JunctionExecutionPlan>,
        val contextPlanBinders: Map<String, List<String>> = emptyMap(),
    )

    private fun buildExecutionPlans(junctions: List<JunctionMeta>): Map<String, JunctionExecutionPlan> {
        val resolvedJunctions = linkedMapOf<String, JunctionExecutionPlan>()
        val resolvedContextJunctions = linkedMapOf<String, Set<JunctionExecutionPlan>>()
        val addedContextToSetOfJunctionIdsMap = mutableMapOf<String, MutableSet<String>>()
        val remaining = junctions.toMutableList()
        // we will allow one context-only pass before another non-context junction
        // must be identified before erroring out
        var contextOnlyPassIterator = 0
        var potentialContextJunctionIssue = mutableListOf<String>()

        // execution plan loop
        while (remaining.any { it.dependsOnKind != DependsOnKind.Context }) {
            val junctionsThatAreReadyToBeGivenExecutionPlans = remaining
                .filter { j ->
                    when (j.dependsOnKind) {
                        DependsOnKind.Context -> true
                        DependsOnKind.Junction -> j.dependsOn.all { dep ->
                            if (resolvedJunctions.containsKey(dep))
                                true
                            else {
                                val contextDepExecutionPlans = resolvedContextJunctions[dep]

                                if (contextDepExecutionPlans.isNullOrEmpty()) {
                                    false
                                } else {
                                    val precedingJunctions = contextDepExecutionPlans
                                        .map { plan ->
                                            plan.junctionExecutionLayerList[plan.junctionExecutionLayerList.size - 2] // one item list
                                                .first() // one item set
                                        }.joinToString(",")

                                    val result = contextDepExecutionPlans
                                        .firstOrNull { plan ->
                                            val precedingJunction = plan.junctionExecutionLayerList[plan.junctionExecutionLayerList.size - 2] // one item list
                                                .first() // one item set

                                            j.dependsOn.contains(precedingJunction)
                                        } != null

                                    if (!result) {
                                        println("WARNING: Potential context junction issue: ${j.id}")
                                        println()

                                        potentialContextJunctionIssue.add("""
                                            Junction ${j.id} is dependent on a Context Junction ($dep) but is missing an upstream dependency for it.
                                            
                                            Because Context Junctions are reusable across many context providers, the Junction depending on it must additionally depend on a non-Context Junction
                                            which is meant to precede the Context Junction and add the necessary context.
                                            
                                            Available junctions are: $precedingJunctions
                                        """.trimIndent())
                                    }

                                    result
                                }
                            }

                        }
                        DependsOnKind.Nothing -> true
                    }
                }

            val areOnlyContextJunctionsReady = junctionsThatAreReadyToBeGivenExecutionPlans.all { it.dependsOnKind == DependsOnKind.Context }

            if (areOnlyContextJunctionsReady) {
                contextOnlyPassIterator++

                if (contextOnlyPassIterator > CONTEXT_ONLY_PASS_THRESHOLD) {
                    val cycleIds = remaining.filter{ it.dependsOnKind != DependsOnKind.Context}.joinToString { it.id }

                    if (potentialContextJunctionIssue.isNotEmpty()) {
                        potentialContextJunctionIssue.joinToString("\n")
                            .apply(::error)
                    }

                    error("Cyclic or unresolved junction dependencies detected: $cycleIds")
                }
            } else {
                contextOnlyPassIterator = 0
            }

            if (contextOnlyPassIterator == CONTEXT_ONLY_PASS_THRESHOLD) {
//                println("\nContext only pass being run\n")
//                println(junctionsThatAreReadyToBeGivenExecutionPlans)
//                println("\n")
            }

            junctionsThatAreReadyToBeGivenExecutionPlans.forEach { j ->
                println("Processing junction: ${j.id}")

                j.contextAdded.forEach { context ->
                    val foundAtContext = addedContextToSetOfJunctionIdsMap[context]

                    if (foundAtContext != null) {
                        foundAtContext.add(j.id)
                    } else {
                        addedContextToSetOfJunctionIdsMap[context] = mutableSetOf(j.id)
                    }
                }


                // plan builder
                var plans: List<JunctionExecutionPlan>
                when (j.dependsOnKind) {
                    DependsOnKind.Nothing -> plans = emptyList()
                    DependsOnKind.Context -> {

                        val applicableJunctionsForThisContextJunction = resolvedJunctions
                            .filter { it.value.contextsAvailableAtThisJunction.containsAll(j.dependsOn)}
                            .map { it.value }

//                        println("----Plan Builder--------------------------------")
//                        println(j.id)
//                        println("------------------------------------------------")
//                        println(applicableJunctionsForThisContextJunction)

                        plans = applicableJunctionsForThisContextJunction
                            .map {
                                JunctionExecutionPlan(
                                    junctionId = j.id,
                                    contextsAvailableAtThisJunction = j.contextAdded.toSet(),
                                    it.junctionExecutionLayerList + listOf(setOf(j.id))
                                )
                            }
                        }
                    DependsOnKind.Junction -> plans = j.dependsOn.mapNotNull { dep ->
                        if (resolvedJunctions.containsKey(dep))
                            resolvedJunctions[dep]
                        else {
                            println("---------DependsOnKind.Junction for DependsOnKind.Context---------\n")
                            println(j.id)
                            println(j.dependsOn)
                            println(resolvedContextJunctions)

                            val contextDepExecutionPlans = resolvedContextJunctions[dep]



                            contextDepExecutionPlans!!
                                .first { plan ->
                                    val precedingJunction = plan.junctionExecutionLayerList[plan.junctionExecutionLayerList.size - 2] // one item list, second from the end
                                        .first() // one item set

                                    j.dependsOn.contains(precedingJunction)
                                }
                        }
                    }
                }

                when (j.dependsOnKind) {
                    DependsOnKind.Context -> {
                        resolvedContextJunctions[j.id] = plans.toSet()
                    }
                    else -> {
                        val mergedLayers = mutableListOf<Set<String>>()
                        plans.forEach { plan ->
                            plan.junctionExecutionLayerList.forEachIndexed { index, layer ->
                                val existing = mergedLayers.getOrNull(index)?.toMutableSet() ?: mutableSetOf()
                                existing += layer
                                if (index >= mergedLayers.size) {
                                    mergedLayers.add(existing)
                                } else {
                                    mergedLayers[index] = existing
                                }
                            }
                        }

                        val contextAddedByPlans = plans.fold(emptySet<String>()) { acc, it ->
                            it.contextsAvailableAtThisJunction + acc
                        }

                        val seen = linkedSetOf<String>()
                        val deduped = mergedLayers
                            .map { layer ->
                                layer.filter { id ->
                                    if (seen.contains(id)) false else {
                                        seen += id
                                        true
                                    }
                                }.toSet()
                            }
                            .filter { it.isNotEmpty() }

                        val plan = JunctionExecutionPlan(
                            junctionId = j.id,
                            contextsAvailableAtThisJunction = j.contextAdded.toSet() + contextAddedByPlans,
                            junctionExecutionLayerList = deduped + listOf(setOf(j.id))
                        )

                        resolvedJunctions[j.id] = plan
                    }
                }



                if (j.dependsOnKind != DependsOnKind.Context) {
                    remaining.removeIf { it.id == j.id }
                }
            }
        }

        return resolvedJunctions
    }

    private fun junctionExecutionPlanWithAllContextsAdded(junctionExecutionPlan: JunctionExecutionPlan, junctionExecutionPlanMap: Map<String, JunctionExecutionPlan>): JunctionExecutionPlan {
        println("-------junctionExecutionPlanWithAllContextsAdded--------")
        println(junctionExecutionPlan)
        println(junctionExecutionPlanMap)
        println("--------------------------------------------------------")

        val contextsAddedPriorToThisJunction = junctionExecutionPlan
            .junctionExecutionLayerList
            .fold(emptySet<String>()) { acc, junctionIds ->
                junctionIds.fold(acc) { acc2, junctionId ->
                    if (junctionId == junctionExecutionPlan.junctionId) {
                        acc2
                    } else {
                        acc2 + junctionExecutionPlanMap[junctionId]!!.contextsAvailableAtThisJunction
                    }
                }
            }

        return junctionExecutionPlan.copy(
            contextsAvailableAtThisJunction = contextsAddedPriorToThisJunction + junctionExecutionPlan.contextsAvailableAtThisJunction
        )
    }

    private fun generateGraph(
        packageName: String,
        diLibrary: DependencyInjectionLibrary,
        junctions: List<JunctionMeta>,
        portals: List<PortalMeta>,
        contexts: List<ContextMeta>,
    ) {
        val file = codeGenerator.createNewFile(
            Dependencies(false),
            packageName,
            "GeneratedJunctionGraph",
            "kt"
        )

        file.writer().use { w ->
            w.appendLine("package $packageName")
            w.appendLine()
            // TODO: This is wrong and should use something dynamic
            w.appendLine("import org.jgwiz.junction.user.*")

            // imports for junctions
            junctions.forEach {
                w.appendLine("import ${it.qualifiedName}")
            }

            // imports for portals
            portals.forEach {
                w.appendLine("import ${it.qualifiedName}")
            }

//            contexts.forEach {
//                w.appendLine("import ${it.qualifiedName}")
//            }

            when (diLibrary) {
                DependencyInjectionLibrary.Spring -> {
                    w.appendLine("import org.springframework.stereotype.Component")
                    w.appendLine("import org.springframework.beans.factory.annotation.Autowired")
                    w.appendLine("import org.springframework.beans.factory.annotation.Qualifier")
                }
            }

            w.appendLine()

            when (diLibrary) {
                DependencyInjectionLibrary.Spring -> w.appendLine("@Component")
            }

            w.appendLine("class GeneratedJunctionGraph @Autowired constructor(")
            portals.forEach {
                // TODO: this is a di library thing that needs major cleanup
                w.appendLine("    @param:Qualifier(\"${it.frameworkId}\")")
                w.appendLine("    val ${it.frameworkId}: ${it.qualifiedName.substringAfterLast(".")},")
            }
            w.appendLine(") : JunctionGraph {")
            w.appendLine()
            w.appendLine("    override val junctions: Map<String, JunctionDefinition> = mapOf(")

            junctions.forEachIndexed { idx, j ->
                val comma = if (idx == junctions.lastIndex) "" else ","
                val dependsOnLiteral = if (j.dependsOn.isEmpty()) {
                    "emptyList()"
                } else {
                    "listOf(${j.dependsOn.joinToString { "\"$it\"" }})"
                }

                val simpleName = j.qualifiedName.substringAfterLast(".")
                val portalIdLiteral = j.portalId?.let { "\"$it\"" } ?: "null"
                val inputDataClassLiteral = j.inputDataClass?.let {
                    "${it}::class.java"
                } ?: "null"

                val inputDataIdLiteral = j.inputDataId?.let { "\"$it\"" } ?: "null"
                val generatedInvoker = generateInvokerForJunction(j, portals, contexts)

                w.appendLine(
                    "        \"${j.id}\" to JunctionDefinition(" +
                            "id = \"${j.id}\"," +
                            " kind = JunctionKind.${j.kind.name}," +
                            " dependsOn = $dependsOnLiteral," +
                            " dependsOnKind = DependsOnKind.${j.dependsOnKind.name}," +
                            " junction = $simpleName," +
                            " portalId = $portalIdLiteral," +
                            " inputDataClass = $inputDataClassLiteral," +
                            " inputDataId = $inputDataIdLiteral," +
                            " invoker = $generatedInvoker," +
                            ")$comma"
                )
            }

            w.appendLine("    )")
            w.appendLine()
            w.appendLine("    override val portals: Map<String, ExternalPortal> = mapOf(")

            portals.forEachIndexed { idx, p ->
                w.appendLine("        \"${p.id}\" to ${p.frameworkId},")
            }

            w.appendLine("    )")

            w.appendLine("    override val executionPlans: Map<String, JunctionExecutionPlan> = mapOf(")

            val executionPlans = buildExecutionPlans(junctions)
            executionPlans.onEachIndexed { idx, entry ->
                val comma = if (idx == executionPlans.size - 1) "" else ","
                val layers = entry.value.junctionExecutionLayerList
                    .joinToString(", ") { layer ->
                        "setOf(${layer.joinToString { "\"$it\"" }})"
                    }
                val contexts = entry.value.contextsAvailableAtThisJunction
                    .joinToString(", ") { context ->
                        "\"$context\""
                    }
                val junctionId = entry.value.junctionId

                w.appendLine(
                    "        \"${entry.key}\" to JunctionExecutionPlan(\"$junctionId\", setOf($contexts), listOf($layers))$comma"
                )
            }

            w.appendLine("    )")


            // end of generated class
            w.appendLine("}")
        }
    }

    private fun generateInvokerForJunction(
        junctionMeta: JunctionMeta,
        portals: List<PortalMeta>,
        contexts: List<ContextMeta>
    ): String {
        val contextId = junctionMeta.refinementContextId
            ?: error("Junction '${junctionMeta.id}' has no required context ID")

        val context = contexts.singleOrNull { it.id == contextId }
            ?: error("Expected exactly one context with ID '$contextId' for junction '${junctionMeta.id}'")

        val portal = if (junctionMeta.kind == JunctionKind.External) {
            val portalId = junctionMeta.portalId
                ?: error("External junction '${junctionMeta.id}' has no portal ID")

            portals.singleOrNull { it.id == portalId }
                ?: error("Expected exactly one portal with ID '$portalId' for junction '${junctionMeta.id}'")
        } else {
            null
        }

        return generateInvokerForJunction(junctionMeta, portal, context)
    }

    private fun generateInvokerForJunction(junctionMeta: JunctionMeta, portal: PortalMeta?, context: ContextMeta): String =
        when (junctionMeta.kind) {
            JunctionKind.Initializing -> {
                """
                    object : JunctionInvoker {
                        override fun invoke(
                            context: RefinementContext,
                            inputData: Any?,
                            portal: ExternalPortal?
                        ): JunctionResult {
                    
                            check(context is ${context.qualifiedName}) {
                                "Refinement context for ${junctionMeta.id} is not of the correct type: ${'$'}{context::class.simpleName}"
                            }
                    
                            checkNotNull(inputData) {
                                "Input Data for ${junctionMeta.id} cannot be null, but was"
                            }
                    
                            check(inputData is ${junctionMeta.inputDataClass}) {
                                "Input Data for ${junctionMeta.id} is not of the correct type: ${'$'}{inputData::class.simpleName}"
                            }
                    
                            return ${junctionMeta.simpleName}.refine(context, inputData)
                        }
                    }
                """.trimIndent()
            }
            JunctionKind.External -> {
                """
                    object : JunctionInvoker {
                        override fun invoke(
                            context: RefinementContext,
                            inputData: Any?,
                            portal: ExternalPortal?
                        ): JunctionResult {
                    
                            check(context is ${context.qualifiedName}) {
                                "Refinement context for ${junctionMeta.id} is not of the correct type: ${'$'}{context::class.simpleName}"
                            }
                            
                            checkNotNull(portal) {
                                "External Portal is required for ${junctionMeta.id}, but was null"
                            }
                    
                            check(portal is UniversityJunctionExternalPortal) {
                                "External Portal is required to be ${portal!!.qualifiedName}, but was ${'$'}{portal::class.simpleName}"
                            }
                    
                            return ${junctionMeta.simpleName}.refine(context, portal)
                        }
                    }
                """.trimIndent()
            }
            JunctionKind.Internal -> {
                """
                    object : JunctionInvoker {
                        override fun invoke(
                            context: RefinementContext,
                            inputData: Any?,
                            portal: ExternalPortal?
                        ): JunctionResult {
                    
                            check(context is ${context.qualifiedName}) {
                                "Refinement context for ${junctionMeta.id} is not of the correct type: ${'$'}{context::class.simpleName}"
                            }
                    
                            return ${junctionMeta.simpleName}.refine(context)
                        }
                    }
                """.trimIndent()
            }
        }


    data class JunctionMeta(
        val id: String,
        val kind: JunctionKind,
        val dependsOn: List<String>,
        val dependsOnKind: DependsOnKind,
        val contextAdded: List<String>,
        val refinementContextId: String?,
        val qualifiedName: String,
        val simpleName: String,
        val portalId: String?,
        val inputDataClass: String?,
        val inputDataId: String? = null,
    )

    /**
     * TODO: framework id is doing double work here and we should have another column called memberName
     */
    data class PortalMeta(
        val id: String,
        val qualifiedName: String,
        val frameworkId: String,
    )

    data class ContextMeta(
        val id: String,
        val qualifiedName: String,
    )
}