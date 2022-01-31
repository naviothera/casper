package com.navio.apollo.casper

import org.springframework.stereotype.Component
import javax.persistence.EntityManager
import javax.persistence.PersistenceContext
import javax.transaction.Transactional

/**
 * Simple Component providing a way for Spring to proxy for transactional needs and expose a
 * receiver to the caller that uses an EntityManager directly within the transaction.
 */
@Component
class EntityManagerTransactionContext {
    @PersistenceContext
    private lateinit var entityManager: EntityManager

    /**
     * Receives an entityManager in it's current state, without creating or
     * verifying the current transactionality.
     */
    fun <T> asIs(f: EntityManager.() -> T): T {
        return f.invoke(entityManager)
    }

    /**
     * Receives an entityManager that is guaranteed to be in a Transaction (this will
     * use the current transaction if available or create a new transaction if not).
     */
    @Transactional
    fun <T> inTransaction(f: EntityManager.() -> T): T {
        return f.invoke(entityManager)
    }

    /**
     * Receives an entityManager that is operating in a new Transaction.
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    fun <T> inNewTransaction(f: EntityManager.() -> T): T {
        return f.invoke(entityManager)
    }
}
