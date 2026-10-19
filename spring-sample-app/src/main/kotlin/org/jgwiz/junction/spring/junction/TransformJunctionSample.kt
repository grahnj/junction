package org.jgwiz.junction.spring.junction

import org.jgwiz.junction.spring.entity.StudentClassroomRequest
import org.jgwiz.junction.spring.entity.UniversityPipelineData
import org.jgwiz.junction.spring.helper.Junc
import org.jgwiz.junction.user.DependsOnKind
import org.jgwiz.junction.user.JunctionResult
import org.jgwiz.junction.user.TransformJunction
import org.jgwiz.junction.user.TransformJunctionSpec

@TransformJunctionSpec(
    id = Junc.JUNCTION_TRANSFORM_TO_STUDENT_CLASSROOM_REQUEST_LIST,
    contextAdded = [Junc.CONTEXT_STUDENT_CLASSROOM_REQUEST],
    refinementContextId = Junc.REFINEMENT_CONTEXT_UNIVERSITY_PIPELINE,
    dependsOnKind = DependsOnKind.Junction,
    dependsOn = [Junc.JUNCTION_INPUT_CLASSROOM_ID, Junc.JUNCTION_EXTERNAL_GET_STUDENT_LIST_BY_CLASSROOM_ID],
)
data object TransformJunctionSample : TransformJunction<UniversityPipelineData> {

    data class LocalContext(
        private val fullContext: UniversityPipelineData
    ) {
        val classroomId get() = fullContext.classroomId!!
        val studentList get() = fullContext.studentList!!
    }

    override fun refine(context: UniversityPipelineData): JunctionResult {
        val localContext = LocalContext(context)

        val studentClassroomRequestList = localContext.studentList
            .map {
                StudentClassroomRequest(
                    classroomId = localContext.classroomId,
                    studentId = it.id,
                )
            }

        context.studentClassroomRequestList = studentClassroomRequestList

        return JunctionResult.Success(
            message = "Transform completed for TransformJunctionSample",
            contextSnapshot = context.asSnapshot())
    }

}