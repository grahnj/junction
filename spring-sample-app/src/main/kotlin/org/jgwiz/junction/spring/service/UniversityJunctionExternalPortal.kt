package org.jgwiz.junction.spring.service

import org.jgwiz.junction.spring.entity.Student
import org.jgwiz.junction.spring.entity.StudentClassroomResponse
import org.jgwiz.junction.spring.entity.StudentClassroomRequest
import org.jgwiz.junction.spring.helper.Junc
import org.jgwiz.junction.user.ExternalJunctionPortalSpec
import org.jgwiz.junction.user.ExternalPortal
import org.springframework.stereotype.Service
import kotlin.random.Random

@Service("universityJunctionExternalPortal")
@ExternalJunctionPortalSpec(
    id = Junc.EXTERNAL_PORTAL_UNIVERSITY_PIPELINE,
    frameworkId = "universityJunctionExternalPortal"

)
class UniversityJunctionExternalPortal : ExternalPortal {
    fun getStudentsByClassroomId(classroomId: Int): List<Student> =
        listOf(Student(1), Student(2), Student(3))

    fun createStudentClassroom(studentClassroomRequest: StudentClassroomRequest): StudentClassroomResponse {
        return StudentClassroomResponse(
            studentClassroomId = 42,
            classroomId = studentClassroomRequest.classroomId,
            studentId = studentClassroomRequest.studentId,
        )
    }

    fun createAllStudentClassrooms(studentClassroomRequestList: List<StudentClassroomRequest>): List<StudentClassroomResponse> {
        return studentClassroomRequestList.map{
            StudentClassroomResponse(
                studentClassroomId = Random.nextInt(),
                classroomId = it.classroomId,
                studentId = it.studentId,
            )
        }
    }
}