package org.jgwiz.junction.processing

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import org.jgwiz.junction.user.DependencyInjectionLibrary
import org.jgwiz.junction.user.DependsOnKind
import org.jgwiz.junction.user.ExternalJunctionPortalSpec
import org.jgwiz.junction.user.ExternalJunctionSpec
import org.jgwiz.junction.user.FanoutJunctionSpec
import org.jgwiz.junction.user.InputJunctionDataSpec
import org.jgwiz.junction.user.InputJunctionSpec
import org.jgwiz.junction.user.JunctionExecutionPlan
import org.jgwiz.junction.user.JunctionGraphConfig
import org.jgwiz.junction.user.JunctionInvoker
import org.jgwiz.junction.user.JunctionKind
import org.jgwiz.junction.user.RefinementContextSpec

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

        val fanoutJunctions = resolver.getSymbolsWithAnnotation(
            FanoutJunctionSpec::class.qualifiedName!!
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
        fanoutJunctions.forEach { collectFanoutJunction(it, junctionMetas) }

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

        val requiredContextId = annotation.arguments
            .first { it.name?.asString() == "requiredContextId" }
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
            requiredContextId = requiredContextId,
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

        val contextAddedArg = annotation.arguments
            .first { it.name?.asString() == "contextAdded" }
            .value as List<*>

        val requiredContextId = annotation.arguments
            .first { it.name?.asString() == "requiredContextId" }
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
            requiredContextId = requiredContextId,
        )
    }

    private fun collectFanoutJunction(
        decl: KSClassDeclaration,
        out: MutableList<JunctionMeta>
    ) {
        val annotation = decl.annotations
            .first { it.shortName.asString() == "FanoutJunctionSpec" }

        val id = annotation.arguments
            .first { it.name?.asString() == "id" }
            .value as String

        val dependsOnArg = annotation.arguments
            .first { it.name?.asString() == "dependsOn" }
            .value as List<*>

        val requiredContextId = annotation.arguments
            .first { it.name?.asString() == "requiredContextId" }
            .value as String

        val dependsOn = dependsOnArg.filterIsInstance<String>()

        out += JunctionMeta(
            id = id,
            kind = JunctionKind.Internal,
            dependsOn = dependsOn,
            dependsOnKind = DependsOnKind.Junction,
            contextAdded = emptyList(),
            qualifiedName = decl.qualifiedName!!.asString(),
            simpleName = decl.simpleName.asString(),
            requiredContextId = requiredContextId,
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

    private fun buildExecutionPlans(junctions: List<JunctionMeta>): Map<String, JunctionExecutionPlan> {
        val resolved = linkedMapOf<String, JunctionExecutionPlan>()
        val availableContext = mutableMapOf<String, String>()
        val remaining = junctions.toMutableList()

        while (remaining.isNotEmpty()) {
            val ready = remaining.filter { j ->
                when (j.dependsOnKind) {
                    DependsOnKind.Context -> j.dependsOn.all { ctx -> availableContext.containsKey(ctx) }
                    DependsOnKind.Junction -> j.dependsOn.all { dep -> resolved.containsKey(dep) }
                    DependsOnKind.Nothing -> true
                }
            }

            if (ready.isEmpty()) {
                val cycleIds = remaining.joinToString { it.id }
                error("Cyclic or unresolved junction dependencies detected: $cycleIds")
            }

            ready.forEach { j ->
                j.contextAdded.forEach { context -> availableContext[context] = j.id }


                var dependencyPlans: List<JunctionExecutionPlan>
                when (j.dependsOnKind) {
                    DependsOnKind.Nothing -> dependencyPlans = emptyList()
                    DependsOnKind.Context -> {
                        val depIds = j.dependsOn.map { ctx ->
                            availableContext.getValue(ctx)
                        }

                        dependencyPlans = depIds.mapNotNull { dep -> resolved[dep] }
                    }
                    DependsOnKind.Junction -> dependencyPlans = j.dependsOn.mapNotNull { dep -> resolved[dep] }
                }

                val mergedLayers = mutableListOf<Set<String>>()
                dependencyPlans.forEach { plan ->
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

                val plan = JunctionExecutionPlan(deduped + listOf(setOf(j.id)))


                resolved[j.id] = plan
                remaining.removeIf { it.id == j.id }
            }
        }

        return resolved
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
                DependencyInjectionLibrary.Spring4 -> {
                    w.appendLine("import org.springframework.stereotype.Component")
                    w.appendLine("import org.springframework.beans.factory.annotation.Autowired")
                    w.appendLine("import org.springframework.beans.factory.annotation.Qualifier")
                }
            }

            w.appendLine()

            when (diLibrary) {
                DependencyInjectionLibrary.Spring4 -> w.appendLine("@Component")
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

                w.appendLine(
                    "        \"${entry.key}\" to JunctionExecutionPlan(listOf($layers))$comma"
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
        val contextId = junctionMeta.requiredContextId
            ?: error("Junction '${junctionMeta.id}' has no required context ID")

        println(contexts)
        println(junctionMeta)
        println(portals)

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
        val requiredContextId: String?,
        val qualifiedName: String,
        val simpleName: String,
        val portalId: String?,
        val inputDataClass: String?,
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