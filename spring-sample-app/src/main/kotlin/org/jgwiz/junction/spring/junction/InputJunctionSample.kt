package org.jgwiz.junction.spring.junction

import org.jgwiz.junction.spring.entity.UniversityPipelineData
import org.jgwiz.junction.spring.helper.Junc
import org.jgwiz.junction.user.InitializingJunction
import org.jgwiz.junction.user.InputJunctionDataSpec
import org.jgwiz.junction.user.InputJunctionSpec
import org.jgwiz.junction.user.JunctionResult

@InputJunctionSpec(
    id = Junc.JUNCTION_INPUT_CLASSROOM_ID,
    contextAdded = [Junc.CONTEXT_CLASSROOM_ID],
    refinementContextId = Junc.REFINEMENT_CONTEXT_UNIVERSITY_PIPELINE,
)
data object InputJunctionSample: InitializingJunction<UniversityPipelineData, InputJunctionSample.UserInput> {

    @InputJunctionDataSpec(Junc.JUNCTION_INPUT_CLASSROOM_ID)
    data class UserInput(
        val classroomId: Int,
    )

    override fun refine(context: UniversityPipelineData, initializingData: UserInput): JunctionResult {
        context.classroomId = initializingData.classroomId

        return JunctionResult.Success(
            contextSnapshot = context.asSnapshot()
        )
    }
}