package org.jgwiz.junction.spring.entity

import org.jgwiz.junction.user.RefinementContext

data class UniversityPipelineData(
    var studentList: List<Student>? = null,
    var classroomId: Int? = null,
    val studentId: Int? = null,
    var instructorId: Int? = null,

    ) : RefinementContext