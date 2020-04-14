package com.navio.apollo.tests

import com.navio.apollo.model.MyUser
import javax.persistence.EntityManager
import javax.transaction.Transactional
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

/**
 * An external Component with an Autowired EntityManager used to ensure that
 * direct Entity management can be accomplished within the SmokeTest setup.
 */
@Component
class MyUserEntityLoader(@Autowired val entityManager: EntityManager) {
    @Transactional
    fun persistUser(user: MyUser) {
        entityManager.persist(user)
    }
}
