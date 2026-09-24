package org.jgwiz.junction.spring.service

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class UniversityJunctionService @Autowired constructor(
    val externalPortalService: UniversityJunctionExternalPortal,
) {

}