package io.github.terminalis.machtv

import io.github.terminalis.machtv.core.AddonDescriptor
import io.github.terminalis.machtv.core.BoardState
import io.github.terminalis.machtv.core.Core
import io.github.terminalis.machtv.core.CtxState
import io.github.terminalis.machtv.core.ResourceRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class BoardRowFlows(core: Core, scope: CoroutineScope) {
    val home = core.home.rowsIn(core.ctx, scope) { addons, request -> catalogTitle(addons, request.base, request.path.type, request.path.id) }
    val movies = core.movies.rowsIn(core.ctx, scope, ::typeRowTitle)
    val series = core.series.rowsIn(core.ctx, scope, ::typeRowTitle)
    val search = core.search.rowsIn(core.ctx, scope, ::searchRowTitle)
    val seeAll: StateFlow<SeeAllPosters> = SeeAllCache().let { cache ->
        core.catalog.map(cache::posters).flowOn(Dispatchers.Default).stateIn(scope, SharingStarted.Eagerly, SeeAllPosters(null, null, emptyList()))
    }
}

private fun StateFlow<BoardState?>.rowsIn(
    ctx: StateFlow<CtxState?>,
    scope: CoroutineScope,
    title: (List<AddonDescriptor>, ResourceRequest) -> String,
): StateFlow<List<BoardRow>> {
    val cache = BoardRowCache()
    return combine(this, ctx) { board, state ->
        val addons = state?.profile?.addons.orEmpty()
        cache.rows(board) { title(addons, it) }
    }.flowOn(Dispatchers.Default).stateIn(scope, SharingStarted.Eagerly, emptyList())
}
