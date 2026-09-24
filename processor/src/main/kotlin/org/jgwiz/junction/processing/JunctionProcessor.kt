package org.jgwiz.junction.processing

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import org.jgwiz.junction.user.DependencyInjectionLibrary
import org.jgwiz.junction.user.ExternalJunctionPortalSpec
import org.jgwiz.junction.user.ExternalJunctionSpec
import org.jgwiz.junction.user.FanoutJunctionSpec
import org.jgwiz.junction.user.InputJunctionSpec
import org.jgwiz.junction.user.JunctionGraphConfig
import org.jgwiz.junction.user.JunctionKind

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

        val fanoutJunctions = resolver.getSymbolsWithAnnotation(
            FanoutJunctionSpec::class.qualifiedName!!
        ).filterIsInstance<KSClassDeclaration>()

        val portals = resolver.getSymbolsWithAnnotation(
            ExternalJunctionPortalSpec::class.qualifiedName!!
        ).filterIsInstance<KSClassDeclaration>()

        val junctionMetas = mutableListOf<JunctionMeta>()
        val portalMetas = mutableListOf<PortalMeta>()

        externalJunctions.forEach { collectExternalJunction(it, junctionMetas) }
        inputJunctions.forEach { collectInputJunction(it, junctionMetas) }
        fanoutJunctions.forEach { collectFanoutJunction(it, junctionMetas) }

        portals.forEach { collectPortal(it, portalMetas) }

        generateGraph(packageName, diFramework, junctionMetas, portalMetas)

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

        val dependsOn = dependsOnArg.filterIsInstance<String>()

        out += JunctionMeta(
            id = id,
            kind = JunctionKind.External,
            dependsOn = dependsOn,
            qualifiedName = decl.qualifiedName!!.asString(),
            portalId = null, // will be matched via portal spec if needed
            inputDataClass = null
        )
    }

    private fun collectInputJunction(
        decl: KSClassDeclaration,
        out: MutableList<JunctionMeta>
    ) {
        val annotation = decl.annotations
            .first { it.shortName.asString() == "InputJunctionSpec" }

        val id = annotation.arguments
            .first { it.name?.asString() == "id" }
            .value as String

        // Input data type is annotated separately with @InputJunctionDataSpec
        // We only store the junction here; processor can be extended later
        out += JunctionMeta(
            id = id,
            kind = JunctionKind.Initializing,
            dependsOn = emptyList(),
            qualifiedName = decl.qualifiedName!!.asString(),
            portalId = null,
            inputDataClass = null
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

        val dependsOn = dependsOnArg.filterIsInstance<String>()

        out += JunctionMeta(
            id = id,
            kind = JunctionKind.Internal,
            dependsOn = dependsOn,
            qualifiedName = decl.qualifiedName!!.asString(),
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

        out += PortalMeta(
            id = id,
            qualifiedName = decl.qualifiedName!!.asString()
        )
    }

    private fun generateGraph(
        packageName: String,
        diLibrary: DependencyInjectionLibrary,
        junctions: List<JunctionMeta>,
        portals: List<PortalMeta>
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
                w.appendLine("    @param:Qualifier(\"${it.id}\")")
                w.appendLine("    val ${it.id}: ${it.qualifiedName.substringAfterLast(".")},")
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
                    "${it.name}::class.java"
                } ?: "null"

                w.appendLine(
                    "        \"${j.id}\" to JunctionDefinition(" +
                            "id = \"${j.id}\"," +
                            " kind = JunctionKind.${j.kind.name}," +
                            " dependsOn = $dependsOnLiteral," +
                            " junction = $simpleName," +
                            " portalId = $portalIdLiteral," +
                            " inputDataClass = $inputDataClassLiteral" +
                            ")$comma"
                )
            }

            w.appendLine("    )")
            w.appendLine()
            w.appendLine("    override val portals: Map<String, ExternalPortal> = mapOf(")

            portals.forEachIndexed { idx, p ->
                w.appendLine("        \"${p.id}\" to ${p.id},")
            }

            w.appendLine("    )")
            w.appendLine("}")
        }
    }

    data class JunctionMeta(
        val id: String,
        val kind: JunctionKind,
        val dependsOn: List<String>,
        val qualifiedName: String,
        val portalId: String?,
        val inputDataClass: Class<*>?,
    )

    data class PortalMeta(
        val id: String,
        val qualifiedName: String,
    )
}