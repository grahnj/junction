package org.jgwiz.junction.spring.junction

import org.jgwiz.junction.spring.entity.UniversityPipelineData
import org.jgwiz.junction.spring.helper.UniversityJunctionConsts
import org.jgwiz.junction.spring.service.UniversityJunctionExternalPortal
import org.jgwiz.junction.user.DependsOnKind
import org.jgwiz.junction.user.ExternalJunction
import org.jgwiz.junction.user.ExternalJunctionSpec
import org.jgwiz.junction.user.ExternalPortal
import org.jgwiz.junction.user.JunctionResult
import org.jgwiz.junction.user.RefinementContext

@ExternalJunctionSpec(
    id = UniversityJunctionConsts.GET_STUDENT_LIST_BY_CLASSROOM_ID,
    dependsOn = [UniversityJunctionConsts.CONTEXT_CLASSROOM_ID],
    dependsOnKind = DependsOnKind.Context,
    externalPortalId = UniversityJunctionConsts.UNIVERSITY_JUNCTION_EXTERNAL_PORTAL,
    requiredContextId = UniversityJunctionConsts.REFINEMENT_CONTEXT_UNIVERSITY_PIPELINE,
)
object GetStudentListByClassroomId : ExternalJunction<UniversityPipelineData, UniversityJunctionExternalPortal> {
    // here we are setting up a local context to harden exactly which fields
    // we should be using when we run refine
    data class LocalContext(
        private val fullContext: UniversityPipelineData
    ) {
        val classroomId: Int get() = fullContext.classroomId!!
    }

    override fun refine(
        context: UniversityPipelineData,
        externalAccess: UniversityJunctionExternalPortal,
    ) : JunctionResult {
        val localContext = LocalContext(context)

        try {
            val response = externalAccess.getStudentsByClassroomId(
                localContext.classroomId,
            )

            context.studentList = response

            return JunctionResult.Success(
                message = "${response.size} students returned for classroomId ${localContext.classroomId}",
                contextSnapshot = context.asSnapshot()
            )
        } catch (e: Exception) {
            return JunctionResult.Failure(
                error = Error(e),
                contextSnapshot = context.asSnapshot()
            )
        }
    }
}