package org.jgwiz.junction.spring.junction

import org.jgwiz.junction.spring.entity.Student
import org.jgwiz.junction.spring.entity.UniversityPipelineData
import org.jgwiz.junction.spring.helper.UniversityJunctionConsts
import org.jgwiz.junction.user.ExternalPortal
import org.jgwiz.junction.user.FanoutJunction
import org.jgwiz.junction.user.FanoutJunctionSpec
import org.jgwiz.junction.user.JunctionResult
import org.jgwiz.junction.user.RefinementContext

@FanoutJunctionSpec(
    id = UniversityJunctionConsts.SPLIT_CONTEXT_BY_STUDENT,
    dependsOn = [UniversityJunctionConsts.GET_STUDENT_LIST_BY_CLASSROOM_ID, UniversityJunctionConsts.INPUT_CLASSROOM_ID],
    requiredContextId = UniversityJunctionConsts.REFINEMENT_CONTEXT_UNIVERSITY_PIPELINE,
)
object FanOutStudentListForStudentContext : FanoutJunction<UniversityPipelineData> {
    data class LocalContext(
        val fullContext: UniversityPipelineData,
    ) {
        val studentList: List<Student> = fullContext.studentList!!
    }

    override fun refine(context: UniversityPipelineData): JunctionResult.Fanout<UniversityPipelineData> {
        val localContext = LocalContext(context)

        return localContext
            .studentList
            .map {
                localContext.fullContext.copy(
                    studentList = null,
                    studentId = it.id
                )
            }
            .let {
                JunctionResult.Fanout(
                    it,
                )
            }
    }
}