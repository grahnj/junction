package org.jgwiz.junction.spring.service

import org.jgwiz.junction.spring.entity.Student
import org.jgwiz.junction.user.ExternalJunctionPortalSpec
import org.jgwiz.junction.user.ExternalPortal
import org.springframework.stereotype.Service

@Service("universityJunctionExternalPortal")
@ExternalJunctionPortalSpec("universityJunctionExternalPortal")
class UniversityJunctionExternalPortal : ExternalPortal {
    fun getStudentsByClassroomId(classroomId: Int): List<Student> =
        listOf(Student(1), Student(2), Student(3))
}