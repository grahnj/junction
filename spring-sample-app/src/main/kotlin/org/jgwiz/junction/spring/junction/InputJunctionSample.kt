package org.jgwiz.junction.spring.junction

import org.jgwiz.junction.spring.entity.UniversityPipelineData
import org.jgwiz.junction.spring.helper.UniversityJunctionConsts
import org.jgwiz.junction.user.InitializingJunction
import org.jgwiz.junction.user.InputJunctionDataSpec
import org.jgwiz.junction.user.InputJunctionSpec
import org.jgwiz.junction.user.JunctionResult

@InputJunctionSpec(
    id = UniversityJunctionConsts.INPUT_CLASSROOM_ID,
    contextAdded = [UniversityJunctionConsts.CONTEXT_CLASSROOM_ID]
)
data object InputJunctionSample: InitializingJunction<UniversityPipelineData, InputJunctionSample.UserInput> {

    @InputJunctionDataSpec(UniversityJunctionConsts.INPUT_CLASSROOM_ID)
    data class UserInput(
        val classroomId: Int,
    )

    override fun refine(context: UniversityPipelineData, initializingData: UserInput): JunctionResult {
        context.classroomId = initializingData.classroomId

        return JunctionResult.Success()
    }
}