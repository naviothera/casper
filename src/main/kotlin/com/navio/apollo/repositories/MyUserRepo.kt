package com.navio.apollo.repositories

import com.navio.apollo.model.MyUser
import org.springframework.data.jpa.repository.JpaRepository

interface MyUserRepo : JpaRepository<MyUser, Int>
