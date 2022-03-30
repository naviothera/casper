package com.navio.apollo.model

import javax.persistence.Entity
import javax.persistence.Id

@Entity
data class MyUser(
    @Id
    val id: Int = 0,
    var name: String
)
