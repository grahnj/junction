package org.jgwiz.junction.spring.entity

import kotlinx.serialization.Serializable

@Serializable
data class Student(val id: Int)
@Serializable
data class StudentClassroomRequest(val studentId: Int, val classroomId: Int,)
@Serializable
data class StudentClassroomResponse(val studentClassroomId: Int, val studentId: Int, val classroomId: Int,)