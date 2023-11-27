package example;

import ratpack.server.RatpackServer;

public class App {

    public static void main(String[] args) throws Exception {
        RatpackServer.start(server -> server
            .handlers(chain -> chain
                .get(ctx -> ctx.render("Hello World!"))
                .get(":name", ctx -> ctx.render("Hello " + ctx.getPathTokens().get("name") + "!"))
            )
        );
    }
}
