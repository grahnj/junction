package org.jgwiz.junction.spring.junction

import org.jgwiz.junction.spring.entity.UniversityPipelineData
import org.jgwiz.junction.spring.helper.Junc
import org.jgwiz.junction.spring.service.UniversityJunctionExternalPortal
import org.jgwiz.junction.user.ExternalJunction
import org.jgwiz.junction.user.ExternalJunctionSpec
import org.jgwiz.junction.user.JunctionResult

@ExternalJunctionSpec(
    id = Junc.JUNCTION_EXTERNAL_CREATE_STUDENT_CLASSROOM,
    contextAdded = [Junc.CONTEXT_STUDENT_CLASSROOM_RESPONSE_LIST],
    refinementContextId = Junc.REFINEMENT_CONTEXT_UNIVERSITY_PIPELINE,
    externalPortalId = Junc.EXTERNAL_PORTAL_UNIVERSITY_PIPELINE,
    dependsOn = [Junc.JUNCTION_TRANSFORM_TO_STUDENT_CLASSROOM_REQUEST_LIST]
)
data object ExternalPersistJunctionSample : ExternalJunction<UniversityPipelineData, UniversityJunctionExternalPortal> {

    data class LocalContext(
        val context: UniversityPipelineData
    ) {
        val studentClassroomRequestList get() = context.studentClassroomRequestList!!
    }

    override fun refine(
        context: UniversityPipelineData,
        externalAccess: UniversityJunctionExternalPortal
    ): JunctionResult {
        val localContext = LocalContext(context)

        return externalAccess
            .runCatching {
                val response = createAllStudentClassrooms(localContext.studentClassroomRequestList)

                context.studentClassroomResponseList = response
            }.fold(
              onSuccess = {
                  JunctionResult.Success(
                      message = "Successfully created new studentClassrooms: ${context.studentClassroomResponseList!!.map { it.studentClassroomId }.joinToString(", ")}",
                      contextSnapshot = context.asSnapshot(),
                  )
              },
              onFailure = {
                  JunctionResult.Failure(
                      error = it,
                      message = "Failed to create Student Classroom",
                      contextSnapshot = context.asSnapshot(),
                  )
              },
            )
    }
}