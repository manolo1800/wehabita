package com.example.data.local.dao

import com.example.data.local.entities.HomeMemberEntity
import com.example.data.local.entities.TaskEntity
import com.example.data.local.entities.TaskOccurrenceEntity
import com.example.data.local.entities.UserEntity

data class MemberWithUser(
    val member: HomeMemberEntity,
    val user: UserEntity
)

data class OccurrenceWithDetails(
    val occurrence: TaskOccurrenceEntity,
    val task: TaskEntity? = null,
    val completedByUser: UserEntity? = null
)
