package org.jgwiz.junction.spring.entity

import org.jgwiz.junction.spring.helper.UniversityJunctionConsts
import org.jgwiz.junction.user.RefinementContext
import org.jgwiz.junction.user.RefinementContextSpec

@RefinementContextSpec(UniversityJunctionConsts.REFINEMENT_CONTEXT_UNIVERSITY_PIPELINE)
data class UniversityPipelineData(
    var studentList: List<Student>? = null,
    var classroomId: Int? = null,
    val studentId: Int? = null,
    var instructorId: Int? = null,

    ) : RefinementContext