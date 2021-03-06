import ratpack.perf.incl.*
import static ratpack.groovy.Groovy.*

ratpack {
  handlers {
    handler("stop", new StopHandler())

    handler("render") {
      render "ok"
    }

    handler("direct")  {
      response.send("ok")
    }

    for (int i = 0; i < 100; ++ i) {
      handler("handler\$i") { throw new RuntimeException("unexpected") }
    }

    handler("manyHandlers") { response.send() }
  }
}
