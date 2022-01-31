package com.navio.apollo.graphql

import com.navio.apollo.model.MyUser
import com.navio.apollo.repositories.MyUserRepo
import graphql.kickstart.tools.GraphQLQueryResolver
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class QueryResolver : GraphQLQueryResolver {
    @Autowired
    lateinit var userRepo: MyUserRepo

    fun ping(): String {
        return "pong"
    }

    fun findUser(userId: Int): MyUser {
        return userRepo.findById(userId).orElseThrow()
    }

    fun listUsers(): List<MyUser> {
        return userRepo.findAll()
    }
}
