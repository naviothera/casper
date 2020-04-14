package com.navio.apollo.graphql

import com.coxautodev.graphql.tools.GraphQLMutationResolver
import com.navio.apollo.model.MyUser
import com.navio.apollo.repositories.MyUserRepo
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class MutationResolver : GraphQLMutationResolver {
    @Autowired
    lateinit var repo: MyUserRepo

    fun ping(): String {
        return "boing"
    }

    fun createUser(id: Int, username: String): MyUser {
        repo.findById(id).ifPresent({ throw IllegalArgumentException("Cannot create with existing id") })
        return repo.saveAndFlush(MyUser(id, username))
    }

    fun updateUser(id: Int, username: String): MyUser {
        return repo.findById(id).map { user ->
            user.name = username
            repo.saveAndFlush(user)
        }.orElseThrow()
    }

    fun deleteUser(id: Int): MyUser {
        val toDelete = repo.findById(id).orElseThrow()
        repo.delete(toDelete)
        return toDelete
    }
}
