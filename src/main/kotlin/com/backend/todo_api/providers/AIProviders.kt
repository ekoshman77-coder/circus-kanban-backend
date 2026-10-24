package com.backend.todo_api.providers

import com.backend.todo_api.model.AiContextType

interface Contextable {
    fun getContextType(): AiContextType
}
/**
* Universeller Provider für ECHTE User-Daten aus der Datenbank
*/
interface AiGlobalDataProvider<I, T>: Contextable {
    fun getGlobalTrainingPairs(): List<Pair<I, T>>
}

/**
 * Universeller Provider für KÜNSTLICHE Start-Lehrbücher (Seeds)
 */
interface AiInitialSeedProvider<I, T>: Contextable {
    fun getInitialSeeds(): List<Pair<I, T>>
}