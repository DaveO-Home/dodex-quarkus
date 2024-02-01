package golf.handicap.routes

import io.vertx.core.Promise
import io.vertx.mutiny.ext.web.Router

interface HandicapRoutes {

    fun getVertxRouter(handicapPromise: Promise<Void>): Router
    fun setRoutePromise(promise: Promise<Void>)
    fun routes(router: Router): Router

}
