package com.backend.todo_api.providers

import com.backend.todo_api.model.AiContextType

interface Contextable {
    fun getContextType(): AiContextType
}
/**
* Universeller Provider für ECHTE User-Daten aus der Datenbank
*/
interface AiGlobalDataProvider<T>: Contextable {
    fun getGlobalTrainingPairs(): List<Pair<String, T>>
}

/**
 * Universeller Provider für KÜNSTLICHE Start-Lehrbücher (Seeds)
 */
interface AiInitialSeedProvider<T>: Contextable {
    fun getInitialSeeds(): List<Pair<String, T>>
}