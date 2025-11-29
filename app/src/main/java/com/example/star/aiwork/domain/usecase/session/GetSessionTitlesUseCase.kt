package com.example.star.aiwork.domain.usecase.session

import com.example.star.aiwork.domain.model.SessionTitle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GetSessionTitlesUseCase(private val getSessionListUseCase: GetSessionListUseCase) {
    /**
     * Invokes the use case to observe the list of session titles.
     *
     * This use case transforms the full list of [SessionEntity] objects into a
     * lightweight list of [SessionTitle] objects, suitable for UI display.
     * It does so by mapping the results from getSessionListUseCase.
     *
     * @return A Flow that emits a list of [SessionTitle] objects.
     */
    operator fun invoke(): Flow<List<SessionTitle>> {
        return getSessionListUseCase().map { entities ->
            entities.map { entity ->
                SessionTitle(id = entity.id, name = entity.name)
            }
        }
    }
}
