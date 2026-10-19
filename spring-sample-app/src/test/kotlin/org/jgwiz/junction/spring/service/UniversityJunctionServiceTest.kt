package org.jgwiz.junction.spring.service

import org.jgwiz.junction.spring.helper.Junc
import org.jgwiz.junction.spring.junction.InputJunctionSample
import org.jgwiz.junction.user.JunctionLogStatus
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class UniversityJunctionServiceTest @Autowired constructor(
    val universityJunctionService: UniversityJunctionService,
) {

    @Test
    fun `it works`() {

        val inputMap = mapOf(Junc.JUNCTION_INPUT_CLASSROOM_ID to InputJunctionSample.UserInput(17))

        val result = universityJunctionService.runJunction(
            Junc.JUNCTION_EXTERNAL_CREATE_STUDENT_CLASSROOM,
            inputMap
        )

        Assertions.assertEquals(JunctionLogStatus.Info, result.status)
    }
}