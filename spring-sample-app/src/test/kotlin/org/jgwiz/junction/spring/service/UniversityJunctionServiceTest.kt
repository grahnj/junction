package org.jgwiz.junction.spring.service

import org.jgwiz.junction.spring.helper.UniversityJunctionConsts
import org.jgwiz.junction.spring.junction.InputJunctionSample
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class UniversityJunctionServiceTest @Autowired constructor(
    val universityJunctionService: UniversityJunctionService,
) {

    @Test
    fun `it works`() {

        val inputMap = mapOf(UniversityJunctionConsts.INPUT_CLASSROOM_ID to InputJunctionSample.UserInput(17))

        val result = universityJunctionService.runJunction(
            UniversityJunctionConsts.SPLIT_CONTEXT_BY_STUDENT,
            inputMap
        )

        println(result)
    }
}