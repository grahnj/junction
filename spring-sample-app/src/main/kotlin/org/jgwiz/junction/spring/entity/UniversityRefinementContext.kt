package org.jgwiz.junction.spring.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import org.jgwiz.junction.spring.config.SnapshotJson
import org.jgwiz.junction.spring.helper.Junc
import org.jgwiz.junction.user.RefinementContext
import org.jgwiz.junction.user.RefinementContextSpec

@RefinementContextSpec(Junc.REFINEMENT_CONTEXT_UNIVERSITY_PIPELINE)
@Serializable
data class UniversityPipelineData(
    var studentList: List<Student>? = null,
    var classroomId: Int? = null,
    val studentId: Int? = null,
    var studentClassroomRequest: StudentClassroomRequest? = null,
    var studentClassroomRequestList: List<StudentClassroomRequest>? = null,
    var studentClassroomResponse: StudentClassroomResponse? = null,
    var studentClassroomResponseList: List<StudentClassroomResponse>? = null,
    var instructorId: Int? = null,

    ) : RefinementContext {
    override fun asSnapshot(): kotlinx.serialization.json.JsonObject {
        return SnapshotJson.encodeToJsonElement(this).jsonObject
    }
}