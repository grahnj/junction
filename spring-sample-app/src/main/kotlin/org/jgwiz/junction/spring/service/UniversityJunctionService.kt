package org.jgwiz.junction.spring.service

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

    private fun runJunction(
        config: JunctionRunnerArgs,
    ): JunctionLog =
        DefaultJunctionRunner.runJunction(config, junctionGraph)
}