package com.navio.apollo

import java.util.concurrent.atomic.AtomicReference

inline fun <reified T> memoOf(crossinline supplier: () -> T): () -> T {
    val supplied = AtomicReference<T>()
    return {
        var previous = supplied.get()
        if (null == previous) {
            synchronized(supplied) {
                previous = supplied.get()
                if (null == previous) {
                    previous = supplier()
                    supplied.set(previous)
                }
            }
        }
        previous
    }
}
