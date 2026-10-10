package org.jgwiz.junction.spring.service

import org.jgwiz.junction.spring.entity.UniversityPipelineData
import org.jgwiz.junction.user.DefaultJunctionRunner
import org.jgwiz.junction.user.JunctionGraph
import org.jgwiz.junction.user.JunctionLog
import org.jgwiz.junction.user.JunctionRunnerArgs
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

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