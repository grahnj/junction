package org.jgwiz.junction.spring.config

import org.jgwiz.junction.user.DependencyInjectionLibrary
import org.jgwiz.junction.user.JunctionGraphConfig

@JunctionGraphConfig(
    packageName = "org.jgwiz.junction.spring.graph",
    dependencyInjectionLibrary = DependencyInjectionLibrary.Spring4,
)
object JunctionConfig